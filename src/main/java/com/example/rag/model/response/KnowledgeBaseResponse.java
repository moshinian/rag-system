package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 知识库返回对象。
 */
public record KnowledgeBaseResponse(
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
