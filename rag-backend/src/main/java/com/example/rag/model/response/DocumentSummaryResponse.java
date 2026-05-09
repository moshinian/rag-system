package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档列表项返回对象。
 */
public record DocumentSummaryResponse(
        Long id,
        String documentCode,
        String knowledgeBaseCode,
        String fileName,
        String displayName,
        String fileType,
        String mediaType,
        Long fileSize,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
