package com.example.rag.service.agent;

import java.util.Map;

/**
 * MCP tool 执行结果；Controller 负责映射为 MCP tools/call result。
 */
public record McpToolResult(
        String toolName,
        boolean isError,
        Map<String, Object> structuredContent,
        String errorMessage,
        Long durationMs
) {
    /** 构造成功工具结果。 */
    public static McpToolResult success(String toolName, Map<String, Object> structuredContent, Long durationMs) {
        return new McpToolResult(toolName, false, structuredContent, null, durationMs);
    }

    /** 构造业务失败工具结果。 */
    public static McpToolResult failure(String toolName, String errorMessage, Long durationMs) {
        return new McpToolResult(toolName, true, Map.of(), errorMessage, durationMs);
    }
}
