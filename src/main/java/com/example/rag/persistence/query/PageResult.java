package com.example.rag.persistence.query;

import java.util.List;

/**
 * 分页查询结果。
 */
public record PageResult<T>(
        List<T> records,
        long total,
        long pageNo,
        long pageSize
) {
}
