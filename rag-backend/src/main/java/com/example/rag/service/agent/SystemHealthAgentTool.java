package com.example.rag.service.agent;

import com.example.rag.service.SystemHealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 只读系统健康检查工具。
 */
@Component
public class SystemHealthAgentTool implements McpTool {
    public static final String TOOL_NAME = "system.health.check";
    private final SystemHealthService systemHealthService;
    private final ObjectMapper objectMapper;

    /** 构造SystemHealthAgentTool。 */
    public SystemHealthAgentTool(SystemHealthService systemHealthService, ObjectMapper objectMapper) {
        this.systemHealthService = systemHealthService;
        this.objectMapper = objectMapper;
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
        Map<String, Object> output = objectMapper.convertValue(systemHealthService.currentStatus(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        return McpToolResult.success(TOOL_NAME, output, AgentToolSupport.elapsedMillis(startedAt));
    }

    private String toolDescription() {
        return "检查 Java 后端关键依赖和服务健康状态。";
    }
}
