package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 文档向量化返回对象。
 */
public record DocumentEmbeddingResponse(
        @JsonSerialize(using = ToStringSerializer.class)
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
