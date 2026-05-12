package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 文档列表项返回对象。
 */
public record DocumentSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String documentCode,
        String knowledgeBaseCode,
        String fileName,
        String displayName,
        String fileType,
        String mediaType,
        Long fileSize,
        String status,
        String disabledFromStatus,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
