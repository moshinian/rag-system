package com.example.rag.common.id;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 本地单实例使用的静态 WorkerId。 */
@Component
@ConditionalOnProperty(prefix = "rag.id.worker-lease", name = "mode", havingValue = "static", matchIfMissing = true)
public class StaticSnowflakeWorkerIdAllocator implements SnowflakeWorkerIdAllocator {
    private final long workerId;

    public StaticSnowflakeWorkerIdAllocator(SnowflakeIdProperties properties) {
        this.workerId = properties.workerId();
    }

    @Override
    public long workerId() {
        return workerId;
    }

    @Override
    public boolean canGenerateIds() {
        return true;
    }

    @Override
    public String description() {
        return "static:" + workerId;
    }
}
