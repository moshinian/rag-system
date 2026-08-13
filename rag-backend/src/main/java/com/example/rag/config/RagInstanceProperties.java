package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 当前进程在集群中的稳定可观测身份。 */
@ConfigurationProperties(prefix = "rag")
public record RagInstanceProperties(String instanceId) {
    public RagInstanceProperties {
        instanceId = instanceId == null || instanceId.isBlank() ? "local" : instanceId.trim();
    }
}
