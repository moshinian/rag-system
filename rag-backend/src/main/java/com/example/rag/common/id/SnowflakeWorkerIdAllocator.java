package com.example.rag.common.id;

/** 为本 JVM 提供唯一 Snowflake WorkerId，并暴露租约健康状态。 */
public interface SnowflakeWorkerIdAllocator {
    long workerId();

    boolean canGenerateIds();

    String description();
}
