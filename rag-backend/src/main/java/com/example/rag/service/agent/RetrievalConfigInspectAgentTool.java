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

    /** 构造RetrievalConfigInspectAgentTool。 */
    public RetrievalConfigInspectAgentTool(RagRetrievalProperties retrievalProperties) {
        this.retrievalProperties = retrievalProperties;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public McpToolDefinition definition() {
        return McpToolDefinition.readOnlyLow(TOOL_NAME, TOOL_NAME, toolDescription());
    }

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
        return McpToolResult.success(
                TOOL_NAME,
                output,
                AgentToolSupport.elapsedMillis(startedAt)
        );
    }

    private String toolDescription() {
        return "检查当前非敏感检索配置，解释 Dense、Hybrid 和 keyword 分支行为参数。";
    }
}
