package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步线程池配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.executor")
public class RagExecutorProperties {

    private Integer corePoolSize = 4;

    private Integer maxPoolSize = 8;

    private Integer queueCapacity = 100;

    private Integer awaitTerminationSeconds = 30;

    private String threadNamePrefix = "rag-indexing-";
}
