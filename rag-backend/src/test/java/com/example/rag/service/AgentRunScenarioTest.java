package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.integration.agent.AgentRuntimeClient;
import com.example.rag.model.dto.AgentRuntimeActionDraft;
import com.example.rag.model.dto.AgentRuntimeResponse;
import com.example.rag.model.dto.AgentRuntimeStepResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Agent 演示场景验收测试。 */
@ExtendWith(MockitoExtension.class)
class AgentRunScenarioTest {
    private static final List<String> GRAPH_NODES = List.of(
            "parse_goal",
            "system_health_check",
            "kb_readiness_check",
            "documents_status_scan",
            "indexing_tasks_scan",
            "diagnose",
            "recommend_actions",
            "generate_report"
    );

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private AgentStepRepository agentStepRepository;

    @Mock
    private AgentActionRepository agentActionRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private AgentRuntimeClient agentRuntimeClient;

    @Mock
    private DocumentIndexingService documentIndexingService;

    @Mock
    private EmbeddingRebuildService embeddingRebuildService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AgentRunService agentRunService;

    @Test
    void reembedRequiredScenarioShouldPersistRebuildActionAndWaitForConfirmation() {
        stubCommonPersistence();
        when(agentRuntimeClient.run(any())).thenReturn(runtimeResponse(
                "知识库当前不可问答，主要原因是 embedding 配置变化后尚未完成重嵌入。",
                "embedding.rebuild.submit",
                "提交知识库重嵌入任务",
                "{\"kbCode\":\"day20-cn-kb\"}"
        ));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest("诊断这个知识库为什么不能问答", null, null, "tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(response.summary()).contains("重嵌入");
        assertThat(response.steps()).extracting(step -> step.nodeName()).containsExactlyElementsOf(GRAPH_NODES);
        assertThat(response.steps()).allSatisfy(step -> assertThat(step.stepCode()).startsWith("AST-"));
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).actionCode()).startsWith("ACT-");
        assertThat(response.actions().get(0).toolName()).isEqualTo("embedding.rebuild.submit");
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.PENDING_CONFIRMATION);
    }

    @Test
    void failedIndexingTaskScenarioShouldPersistRetryActionAndWaitForConfirmation() {
        stubCommonPersistence();
        when(agentRuntimeClient.run(any())).thenReturn(runtimeResponse(
                "知识库存在失败的索引任务，需要人工确认后重试失败任务。",
                "document.indexing_task.retry",
                "重试失败索引任务",
                "{\"kbCode\":\"day20-cn-kb\",\"taskId\":1001,\"documentCode\":\"DOC-failed-demo\"}"
        ));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest("检查这个知识库有没有索引异常", null, null, "tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(response.summary()).contains("失败的索引任务");
        assertThat(response.steps()).extracting(step -> step.nodeName()).containsExactlyElementsOf(GRAPH_NODES);
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).actionCode()).startsWith("ACT-");
        assertThat(response.actions().get(0).toolName()).isEqualTo("document.indexing_task.retry");
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.PENDING_CONFIRMATION);
        assertThat(response.actions().get(0).actionPayload()).contains("\"taskId\":1001");
        assertThat(response.actions().get(0).actionPayload()).contains("\"documentCode\":\"DOC-failed-demo\"");
    }

    private void stubCommonPersistence() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        knowledgeBase.setKbCode("day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(
                100L,
                201L, 202L, 203L, 204L, 205L, 206L, 207L, 208L,
                300L
        );
        when(snowflakeIdGenerator.nextId("AR-")).thenReturn("AR-100");
        when(snowflakeIdGenerator.nextId("AST-")).thenReturn(
                "AST-201", "AST-202", "AST-203", "AST-204",
                "AST-205", "AST-206", "AST-207", "AST-208"
        );
        when(snowflakeIdGenerator.nextId("ACT-")).thenReturn("ACT-300");
        when(agentRunRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentStepRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentActionRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AgentRuntimeResponse runtimeResponse(String summary,
                                                 String toolName,
                                                 String title,
                                                 String actionPayload) {
        return new AgentRuntimeResponse(
                "SUCCEEDED",
                summary,
                GRAPH_NODES.stream()
                        .map(node -> new AgentRuntimeStepResult(
                                node,
                                node.endsWith("_scan") ? node.replace("_", ".") : null,
                                node.endsWith("_scan") ? AgentStepType.TOOL_CALL : AgentStepType.NODE,
                                AgentStepStatus.SUCCEEDED,
                                null,
                                "{}",
                                1L,
                                null
                        ))
                        .toList(),
                List.of(new AgentRuntimeActionDraft(
                        toolName,
                        title,
                        "需要人工确认后执行",
                        AgentActionRiskLevel.MEDIUM,
                        true,
                        actionPayload
                )),
                null
        );
    }
}
