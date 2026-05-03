package com.example.rag.persistence.query;

/**
 * 问答历史分页查询条件。
 */
public record QaHistoryPageQuery(
        Long knowledgeBaseId,
        long pageNo,
        long pageSize
) {
}
