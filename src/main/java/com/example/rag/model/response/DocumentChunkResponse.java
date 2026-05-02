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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
