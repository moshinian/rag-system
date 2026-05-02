package com.example.rag.model.response;

import java.util.List;

/**
 * 通用分页响应。
 */
public record PageResponse<T>(
        List<T> records,
        long total,
        long pageNo,
        long pageSize
) {
}
