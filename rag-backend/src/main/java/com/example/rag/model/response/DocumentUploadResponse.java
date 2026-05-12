package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 文档上传返回对象。
 *
 * 表示原始文档已成功存档，不代表已经完成解析或建立索引。
 */
public record DocumentUploadResponse(
        @JsonSerialize(using = ToStringSerializer.class)
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
