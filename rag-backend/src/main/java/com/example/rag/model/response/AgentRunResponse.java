package com.example.rag.model.response;

import com.example.rag.model.enums.AgentRunStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Agent 运行详情响应。 */
public record AgentRunResponse(
        String runCode,
        String knowledgeBaseCode,
        String goal,
        String question,
        AgentRunStatus status,
        String summary,
        String errorMessage,
        List<AgentStepResponse> steps,
        List<AgentActionResponse> actions,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime finishedAt
) {
}
