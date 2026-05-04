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
        Long documentId,
        String documentCode,
        String knowledgeBaseCode,
        String parserName,
        Integer chunkCount,
        Integer embeddedChunkCount,
        String errorMessage,
        String createdBy,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
