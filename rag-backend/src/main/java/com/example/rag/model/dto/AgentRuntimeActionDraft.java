package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentActionRiskLevel;

/**
 * Python Agent Runtime 返回的推荐动作草案。
 */
public record AgentRuntimeActionDraft(
        String toolName,
        String title,
        String reason,
        AgentActionRiskLevel riskLevel,
        boolean requiresConfirmation,
        String actionPayload
) {
}
