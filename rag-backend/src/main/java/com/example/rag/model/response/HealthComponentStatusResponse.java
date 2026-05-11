package com.example.rag.model.response;

import java.time.Instant;

/**
 * 单个系统组件健康状态。
 */
public record HealthComponentStatusResponse(
        String status,
        String category,
        String endpoint,
        String provider,
        String model,
        Long latencyMs,
        String detail,
        String errorMessage,
        Instant checkedAt
) {
}
