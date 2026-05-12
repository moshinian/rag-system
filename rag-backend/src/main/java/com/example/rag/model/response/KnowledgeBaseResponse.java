package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

/**
 * 知识库返回对象。
 */
public record KnowledgeBaseResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String kbCode,
        String name,
        String description,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
