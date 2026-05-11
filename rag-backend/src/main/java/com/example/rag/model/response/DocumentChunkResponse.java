package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档 chunk 返回对象。
 */
public record DocumentChunkResponse(
        Long id,
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
        Long embeddingRebuildRunId,
        String embeddingUpdatedBy,
        OffsetDateTime embeddingUpdatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
