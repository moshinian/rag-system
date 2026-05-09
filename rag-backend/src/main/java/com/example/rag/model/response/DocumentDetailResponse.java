package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档详情返回对象。
 */
public record DocumentDetailResponse(
        Long id,
        String documentCode,
        String knowledgeBaseCode,
        String fileName,
        String displayName,
        String fileType,
        String mediaType,
        String storagePath,
        Long fileSize,
        String contentHash,
        String status,
        Integer version,
        String source,
        String tags,
        String errorMessage,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
