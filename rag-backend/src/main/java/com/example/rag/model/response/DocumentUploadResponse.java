package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档上传返回对象。
 *
 * 表示原始文档已成功存档，不代表已经完成解析或建立索引。
 */
public record DocumentUploadResponse(
        Long id,
        String documentCode,
        String knowledgeBaseCode,
        String fileName,
        String displayName,
        String fileType,
        String mediaType,
        Long fileSize,
        String storagePath,
        String contentHash,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
