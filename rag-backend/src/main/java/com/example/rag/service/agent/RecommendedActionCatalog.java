package com.example.rag.service.agent;

import com.example.rag.model.enums.AgentActionRiskLevel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Agent 推荐动作白名单；不属于 MCP tools/list，也不能通过 tools/call 执行。
 */
@Component
public class RecommendedActionCatalog {
    public static final String RETRY_INDEXING_TASK_TOOL = "document.indexing_task.retry";
    public static final String SUBMIT_EMBEDDING_REBUILD_TOOL = "embedding.rebuild.submit";

    private final Map<String, RecommendedActionDefinition> actions = Map.of(
            RETRY_INDEXING_TASK_TOOL,
            new RecommendedActionDefinition(RETRY_INDEXING_TASK_TOOL, AgentActionRiskLevel.MEDIUM, true),
            SUBMIT_EMBEDDING_REBUILD_TOOL,
            new RecommendedActionDefinition(SUBMIT_EMBEDDING_REBUILD_TOOL, AgentActionRiskLevel.MEDIUM, true)
    );

    /** 查询推荐动作定义。 */
    public Optional<RecommendedActionDefinition> find(String toolName) {
        return Optional.ofNullable(actions.get(toolName));
    }

    /** 判断推荐动作是否可由人工确认后执行。 */
    public boolean isExecutable(String toolName, AgentActionRiskLevel riskLevel) {
        return find(toolName)
                .filter(action -> action.riskLevel() == riskLevel)
                .filter(RecommendedActionDefinition::requiresConfirmation)
                .isPresent();
    }

    /** 推荐动作定义。 */
    public record RecommendedActionDefinition(
            String toolName,
            AgentActionRiskLevel riskLevel,
            boolean requiresConfirmation
    ) {
    }
}
