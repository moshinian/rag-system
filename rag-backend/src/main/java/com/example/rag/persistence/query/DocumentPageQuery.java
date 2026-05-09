package com.example.rag.persistence.query;

import com.example.rag.model.enums.DocumentStatus;

/**
 * 文档分页查询条件。
 */
public record DocumentPageQuery(
        Long knowledgeBaseId,
        DocumentStatus status,
        long pageNo,
        long pageSize
) {
}
