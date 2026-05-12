package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 知识库启用/恢复结果。
 */
public record KnowledgeBaseEnableResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String kbCode,
        String name,
        String description,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean retryFailedIndexingTasks,
        int retriedFailedTaskCount,
        int skippedDisabledDocumentCount,
        int skippedActiveTaskDocumentCount,
        int skippedRetryLimitDocumentCount,
        List<String> retriedDocumentCodes
) {
}
