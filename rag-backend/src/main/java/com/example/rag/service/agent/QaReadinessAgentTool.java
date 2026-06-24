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

    /** 注入 readiness 服务和对象映射器。 */
    public QaReadinessAgentTool(QuestionAnsweringService questionAnsweringService, ObjectMapper objectMapper) {
        this.questionAnsweringService = questionAnsweringService;
        this.objectMapper = objectMapper;
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

    /** 返回供 planner 理解 readiness 范围的描述。 */
    @Override
    public String description() {
        return "检查指定知识库是否具备问答 readiness，包括索引、向量和重嵌入状态。";
    }

    /** 声明仅接收必填 kbCode 的严格输入 schema。 */
    @Override
    public Map<String, Object> inputSchema() {
        return AgentToolSupport.objectSchema(
                java.util.List.of("kbCode"),
                Map.of("kbCode", AgentToolSupport.stringProperty("知识库编码。"))
        );
    }

    /** 查询知识库 readiness，并转换为结构化工具输出。 */
    @Override
    public McpToolResult call(McpToolContext context) {
        long startedAt = System.nanoTime();
        Map<String, Object> output = objectMapper.convertValue(questionAnsweringService.getReadiness(context.kbCode()), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        return McpToolResult.success(TOOL_NAME, output, AgentToolSupport.elapsedMillis(startedAt));
    }

}
