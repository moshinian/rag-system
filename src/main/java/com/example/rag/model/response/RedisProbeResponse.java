package com.example.rag.model.response;

public record RedisProbeResponse(
        String key,
        String writtenValue,
        String cachedValue,
        boolean matched
) {
}
