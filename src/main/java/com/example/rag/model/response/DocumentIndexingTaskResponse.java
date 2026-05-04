package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档索引任务返回对象。
 */
public record DocumentIndexingTaskResponse(
        Long taskId,
        String taskType,
        String status,
        String taskStage,
        String triggerSource,
        Long documentId,
        String documentCode,
        String knowledgeBaseCode,
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
