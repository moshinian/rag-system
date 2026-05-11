package com.example.rag.model.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 系统健康检查返回对象。
 */
public record HealthStatusResponse(
        String status,
        String serviceName,
        List<String> activeProfiles,
        Map<String, HealthComponentStatusResponse> components,
        Instant checkedAt
) {
}
