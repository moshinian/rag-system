package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 索引任务相关配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.indexing")
public class RagIndexingProperties {
    private int maxRetryCount = 3;
    private Worker worker = new Worker();
    private Recovery recovery = new Recovery();

    @Data
    public static class Worker {
        private boolean enabled = true;
        private Duration pollInterval = Duration.ofSeconds(1);
        private Duration leaseDuration = Duration.ofSeconds(120);
        private Duration heartbeatInterval = Duration.ofSeconds(30);
    }

    @Data
    public static class Recovery {
        private boolean enabled = true;
        private Duration scanInterval = Duration.ofSeconds(10);
        private long staleAfterSeconds = 600;
        private int scanLimit = 20;
    }
}
