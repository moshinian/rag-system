package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档向量化返回对象。
 */
public record DocumentEmbeddingResponse(
        Long documentId,
        String documentCode,
        String knowledgeBaseCode,
        String embeddingModel,
        Integer vectorDimensions,
        Integer batchSize,
        int embeddedChunkCount,
        int failedChunkCount,
        long totalEmbeddedChunkCount,
        OffsetDateTime updatedAt
) {
}
