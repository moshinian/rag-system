package com.example.rag.service.agent;

import com.example.rag.service.QuestionAnsweringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 只读问答 readiness 检查工具。
 */
@Component
public class QaReadinessAgentTool implements McpTool {
    public static final String TOOL_NAME = "kb.readiness.check";
    private final QuestionAnsweringService questionAnsweringService;
    private final ObjectMapper objectMapper;

    /** 构造QaReadinessAgentTool。 */
    public QaReadinessAgentTool(QuestionAnsweringService questionAnsweringService, ObjectMapper objectMapper) {
        this.questionAnsweringService = questionAnsweringService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String title() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "检查指定知识库是否具备问答 readiness，包括索引、向量和重嵌入状态。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentToolSupport.objectSchema(
                java.util.List.of("kbCode"),
                Map.of("kbCode", AgentToolSupport.stringProperty("知识库编码。"))
        );
    }

    @Override
    public McpToolResult call(McpToolContext context) {
        long startedAt = System.nanoTime();
        Map<String, Object> output = objectMapper.convertValue(questionAnsweringService.getReadiness(context.kbCode()), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        return McpToolResult.success(TOOL_NAME, output, AgentToolSupport.elapsedMillis(startedAt));
    }

}
