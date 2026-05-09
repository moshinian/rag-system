package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 索引任务相关配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.indexing")
public class RagIndexingProperties {

    private int maxRetryCount = 3;

    private Recovery recovery = new Recovery();

    @Data
    public static class Recovery {

        private boolean enabled = true;
        private long staleAfterSeconds = 600;
        private int scanLimit = 20;
    }
}
