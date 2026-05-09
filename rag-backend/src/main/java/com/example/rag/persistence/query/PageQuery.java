package com.example.rag.persistence.query;

/**
 * 分页查询参数。
 */
public record PageQuery(
        long pageNo,
        long pageSize
) {

    public long offset() {
        return (pageNo - 1) * pageSize;
    }
}
