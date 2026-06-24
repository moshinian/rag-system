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

    /** 注入系统健康服务和对象映射器。 */
    public SystemHealthAgentTool(SystemHealthService systemHealthService, ObjectMapper objectMapper) {
        this.systemHealthService = systemHealthService;
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

    /** 返回供 planner 理解健康检查范围的描述。 */
    @Override
    public String description() {
        return "检查 Java 后端关键依赖和服务健康状态。";
    }

    /** 声明不接收业务参数的严格空 object schema。 */
    @Override
    public Map<String, Object> inputSchema() {
        return AgentToolSupport.objectSchema(java.util.List.of(), Map.of());
    }

    /** 执行系统健康检查，并转换为结构化工具输出。 */
    @Override
    public McpToolResult call(McpToolContext context) {
        long startedAt = System.nanoTime();
        Map<String, Object> output = objectMapper.convertValue(systemHealthService.currentStatus(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        return McpToolResult.success(TOOL_NAME, output, AgentToolSupport.elapsedMillis(startedAt));
    }

}
