package com.example.rag.common.id;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Snowflake WorkerId 的租赁策略配置。 */
@Data
@ConfigurationProperties(prefix = "rag.id.worker-lease")
public class SnowflakeWorkerLeaseProperties {
    private String mode = "static";
    private String keyPrefix = "rag:snowflake:worker";
    private Duration leaseDuration = Duration.ofSeconds(120);
    private Duration renewalInterval = Duration.ofSeconds(30);
    private int maxWorkerId = 31;
}
