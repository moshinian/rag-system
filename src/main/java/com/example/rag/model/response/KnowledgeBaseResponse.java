package com.example.rag.model.response;

import java.time.OffsetDateTime;

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
