package com.example.rag.common;

import java.time.Instant;

/**
 * 统一接口返回结构。
 *
 * @param <T> 返回数据类型
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp
) {

    /** 构造成功响应。 */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("SUCCESS", "OK", data, requestId, Instant.now());
    }

    /** 构造失败响应。 */
    public static <T> ApiResponse<T> failure(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId, Instant.now());
    }
}
