package com.example.rag.model.response;

import java.time.OffsetDateTime;

public record DocumentUploadResponse(
        Long id,
        String documentCode,
        String knowledgeBaseCode,
        String fileName,
        String displayName,
        String fileType,
        Long fileSize,
        String storagePath,
        String contentHash,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
