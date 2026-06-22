package com.example.rag.model.dto;

/**
 * Agent 工具执行结果。
 */
public record AgentToolResult(
        String toolName,
        boolean success,
        String outputJson,
        String errorMessage,
        Long durationMs
) {
    /** 构造成功工具结果。 */
    public static AgentToolResult success(String toolName, String outputJson, Long durationMs) {
        return new AgentToolResult(toolName, true, outputJson, null, durationMs);
    }

    /** 构造失败工具结果。 */
    public static AgentToolResult failure(String toolName, String errorMessage, Long durationMs) {
        return new AgentToolResult(toolName, false, null, errorMessage, durationMs);
    }
}
