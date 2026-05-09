package com.example.rag.common.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 雪花 ID 配置。
 */
@ConfigurationProperties(prefix = "rag.id")
public record SnowflakeIdProperties(
        long workerId,
        long datacenterId
) {

    /** 校验 workerId 和 datacenterId 的取值范围。 */
    public SnowflakeIdProperties {
        if (workerId < 0 || workerId > 31) {
            throw new IllegalArgumentException("rag.id.worker-id must be between 0 and 31");
        }
        if (datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException("rag.id.datacenter-id must be between 0 and 31");
        }
    }
}
