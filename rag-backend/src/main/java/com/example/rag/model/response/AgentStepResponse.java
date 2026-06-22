package com.example.rag.model.response;

import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;

import java.time.OffsetDateTime;

/** Agent 执行步骤响应。 */
public record AgentStepResponse(
        String stepCode,
        String nodeName,
        String toolName,
        AgentStepType stepType,
        AgentStepStatus status,
        String inputJson,
        String outputJson,
        Long durationMs,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
