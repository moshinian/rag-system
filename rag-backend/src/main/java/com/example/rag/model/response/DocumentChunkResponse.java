package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 文档 chunk 返回对象。
 */
public record DocumentChunkResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long documentId,
        Integer chunkIndex,
        String chunkType,
        String title,
        String content,
        Integer contentLength,
        Integer tokenCount,
        Integer startOffset,
        Integer endOffset,
        String metadataJson,
        String status,
        String embeddingStatus,
        String embeddingModel,
        String embeddingProvider,
        String embeddingProfileFingerprint,
        @JsonSerialize(using = ToStringSerializer.class)
        Long embeddingRebuildRunId,
        String embeddingUpdatedBy,
        OffsetDateTime embeddingUpdatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
