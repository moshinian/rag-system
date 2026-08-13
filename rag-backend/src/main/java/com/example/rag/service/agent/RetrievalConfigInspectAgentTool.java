package com.example.rag.service.agent;

import com.example.rag.config.RagRetrievalProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 只读检索配置检查工具。
 */
@Component
public class RetrievalConfigInspectAgentTool implements McpTool {
    public static final String TOOL_NAME = "retrieval.config.inspect";
    private final RagRetrievalProperties retrievalProperties;

    /** 注入当前生效的检索配置。 */
    public RetrievalConfigInspectAgentTool(RagRetrievalProperties retrievalProperties) {
        this.retrievalProperties = retrievalProperties;
    }

    /** 返回 MCP 工具唯一名称。 */
    @Override
    public String name() {
        return TOOL_NAME;
    }

    /** 返回工具展示标题。 */
    @Override
    public String title() {
        return TOOL_NAME;
    }

    /** 返回供 planner 理解配置检查范围的描述。 */
    @Override
    public String description() {
        return "检查当前非敏感检索配置，解释 Dense、Hybrid 和 keyword 分支行为参数。";
    }

    /** 声明不接收业务参数的严格空 object schema。 */
    @Override
    public Map<String, Object> inputSchema() {
        return AgentToolSupport.objectSchema(java.util.List.of(), Map.of());
    }

    /** 返回非敏感检索配置快照，不暴露凭证或连接信息。 */
    @Override
    public McpToolResult call(McpToolContext context) {
        long startedAt = System.nanoTime();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("defaultMode", retrievalProperties.getDefaultMode());
        output.put("defaultTopK", retrievalProperties.getDefaultTopK());
        output.put("maxTopK", retrievalProperties.getMaxTopK());
        output.put("denseCandidateLimit", retrievalProperties.getDenseCandidateLimit());
        output.put("keywordCandidateLimit", retrievalProperties.getKeywordCandidateLimit());
        output.put("fusionK", retrievalProperties.getFusionK());
        output.put("keywordStrategy", retrievalProperties.getKeywordStrategy());
        output.put("keywordMinTokenLength", retrievalProperties.getKeywordMinTokenLength());
        output.put("keywordMinHitThreshold", retrievalProperties.getKeywordMinHitThreshold());
        RagRetrievalProperties.Rerank rerank = retrievalProperties.getRerank();
        if (rerank != null) {
            Map<String, Object> rerankOutput = new LinkedHashMap<>();
            rerankOutput.put("enabled", rerank.isEnabled());
            rerankOutput.put("model", rerank.getModel());
            rerankOutput.put("candidateLimit", rerank.getCandidateLimit());
            rerankOutput.put("policyVersion", rerank.getPolicyVersion());
            output.put("rerank", rerankOutput);
        }
        return McpToolResult.success(
                TOOL_NAME,
                output,
                AgentToolSupport.elapsedMillis(startedAt)
        );
    }

}
