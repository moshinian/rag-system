package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;

/**
 * Agent 工具白名单定义。
 */
public record AgentToolDefinition(
        String toolName,
        AgentToolExecutionMode executionMode,
        AgentActionRiskLevel maxRiskLevel
) {
}
