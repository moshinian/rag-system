package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 缓存配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.cache")
public class RagCacheProperties {

    private long knowledgeBaseDetailTtlSeconds = 600;

    private long knowledgeBasePageTtlSeconds = 300;

    private long documentDetailTtlSeconds = 300;

    private long documentPageTtlSeconds = 180;

    private long documentChunksTtlSeconds = 180;

    private long qaReadinessTtlSeconds = 60;

    private long qaRetrievalTtlSeconds = 60;
}
