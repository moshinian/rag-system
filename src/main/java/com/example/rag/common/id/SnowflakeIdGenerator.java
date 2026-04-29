package com.example.rag.common.id;

import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    private static final long CUSTOM_EPOCH = 1704067200000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(SnowflakeIdProperties properties) {
        this.workerId = properties.workerId();
        this.datacenterId = properties.datacenterId();

        if (workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId exceeds max value: " + MAX_WORKER_ID);
        }
        if (datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId exceeds max value: " + MAX_DATACENTER_ID);
        }
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();
        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id.");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                currentTimestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    public String nextId(String prefix) {
        return prefix + nextId();
    }

    private long waitUntilNextMillis(long lastTimestamp) {
        long currentTimestamp = timestamp();
        while (currentTimestamp <= lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }

    private long timestamp() {
        return System.currentTimeMillis();
    }
}
