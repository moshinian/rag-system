package com.example.rag.model.response;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;

import java.time.OffsetDateTime;

/** Agent 推荐动作响应。 */
public record AgentActionResponse(
        String actionCode,
        String toolName,
        String title,
        String reason,
        AgentActionRiskLevel riskLevel,
        boolean requiresConfirmation,
        AgentActionStatus status,
        String actionPayload,
        String confirmedBy,
        OffsetDateTime confirmedAt,
        OffsetDateTime executedAt,
        String resultJson,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
