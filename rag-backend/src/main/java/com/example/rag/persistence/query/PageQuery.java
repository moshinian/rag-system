package com.example.rag.persistence.query;

/**
 * 分页查询参数。
 */
public record PageQuery(
        long pageNo,
        long pageSize
) {
    /** 计算当前分页查询的偏移量。 */
    public long offset() {
        return (pageNo - 1) * pageSize;
    }
}
