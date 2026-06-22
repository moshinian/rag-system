package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;

/**
 * Agent 工具白名单定义。
 */
public record AgentToolDefinition(
        String toolName,
        String schemaVersion,
        String description,
        String inputSchema,
        String outputSchema,
        AgentToolExecutionMode executionMode,
        AgentActionRiskLevel maxRiskLevel,
        String sourceType,
        Boolean requiresConfirmation,
        Long timeoutMs
) {
    public AgentToolDefinition(String toolName,
                               AgentToolExecutionMode executionMode,
                               AgentActionRiskLevel maxRiskLevel) {
        this(
                toolName,
                "v2",
                toolName,
                "{}",
                "{}",
                executionMode,
                maxRiskLevel,
                "JAVA",
                executionMode != AgentToolExecutionMode.READ_ONLY || maxRiskLevel != AgentActionRiskLevel.LOW,
                5000L
        );
    }
}
