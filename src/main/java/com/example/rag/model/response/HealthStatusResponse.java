package com.example.rag.model.response;

import java.time.Instant;
import java.util.List;

public record HealthStatusResponse(
        String status,
        String serviceName,
        List<String> activeProfiles,
        Instant checkedAt
) {
}
