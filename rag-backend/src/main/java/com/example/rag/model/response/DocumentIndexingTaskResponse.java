package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 文档索引任务返回对象。
 */
public record DocumentIndexingTaskResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long taskId,
        String taskType,
        String status,
        String taskStage,
        String triggerSource,
        @JsonSerialize(using = ToStringSerializer.class)
        Long documentId,
        String documentCode,
        String knowledgeBaseCode,
        @JsonSerialize(using = ToStringSerializer.class)
        Long parentTaskId,
        String parserName,
        Integer chunkCount,
        Integer embeddedChunkCount,
        Integer retryCount,
        Integer maxRetryCount,
        String errorMessage,
        String createdBy,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime recoveredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
