package com.example.rag.service.agent;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;

import java.util.Map;

/**
 * MCP tools capability 下可被 Agent Runtime 直接调用的业务工具。
 */
public interface McpTool {
    /** MCP tool name。 */
    String name();

    /** MCP tool title。 */
    String title();

    /** MCP tool description。 */
    String description();

    /** 当前工具独立声明的输入 JSON Schema。 */
    Map<String, Object> inputSchema();

    /** 当前工具独立声明的输出 JSON Schema；第一版允许为空。 */
    default Map<String, Object> outputSchema() {
        return Map.of();
    }

    /** 执行只读低风险工具。 */
    McpToolResult call(McpToolContext context);

    /** 返回标准 MCP tool definition。 */
    default McpToolDefinition definition() {
        return McpToolDefinition.of(
                name(),
                title(),
                description(),
                inputSchema(),
                outputSchema(),
                executionMode(),
                maxRiskLevel(),
                requiresConfirmation(),
                timeoutMs()
        );
    }

    /** 工具执行模式。 */
    default AgentToolExecutionMode executionMode() {
        return AgentToolExecutionMode.READ_ONLY;
    }

    /** 工具最大风险等级。 */
    default AgentActionRiskLevel maxRiskLevel() {
        return AgentActionRiskLevel.LOW;
    }

    /** 是否需要人工确认。 */
    default boolean requiresConfirmation() {
        return false;
    }

    /** 工具超时时间。 */
    default Long timeoutMs() {
        return 5000L;
    }
}
