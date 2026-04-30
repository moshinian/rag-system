package com.example.rag.model.response;

/**
 * Redis 探针返回对象。
 */
public record RedisProbeResponse(
        String key,
        String writtenValue,
        String cachedValue,
        boolean matched
) {
}
