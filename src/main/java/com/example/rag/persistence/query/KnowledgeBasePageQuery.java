package com.example.rag.persistence.query;

import com.example.rag.model.enums.KnowledgeBaseStatus;

/**
 * 知识库分页查询条件。
 */
public record KnowledgeBasePageQuery(
        KnowledgeBaseStatus status,
        long pageNo,
        long pageSize
) {
}
