package com.example.rag.common.id;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness 使用的 WorkerId Lease 健康指标。 */
@Component("snowflakeWorkerLease")
public class SnowflakeWorkerLeaseHealthIndicator implements HealthIndicator {
    private final SnowflakeWorkerIdAllocator allocator;

    public SnowflakeWorkerLeaseHealthIndicator(SnowflakeWorkerIdAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public Health health() {
        return allocator.canGenerateIds()
                ? Health.up().withDetail("identity", allocator.description()).build()
                : Health.down().withDetail("identity", allocator.description()).build();
    }
}
