package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 文档详情返回对象。
 */
public record DocumentDetailResponse(
        @JsonSerialize(using = ToStringSerializer.class)
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
        String disabledFromStatus,
        Integer version,
        String source,
        String tags,
        String errorMessage,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
