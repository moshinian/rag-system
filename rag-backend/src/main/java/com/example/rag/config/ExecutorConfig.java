package com.example.rag.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 异步执行器配置。
 */
@Configuration
public class ExecutorConfig {

    private final RagExecutorProperties ragExecutorProperties;

    public ExecutorConfig(RagExecutorProperties ragExecutorProperties) {
        this.ragExecutorProperties = ragExecutorProperties;
    }

    /** 定义索引处理线程池。 */
    @Bean("indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizeCorePoolSize());
        executor.setMaxPoolSize(normalizeMaxPoolSize());
        executor.setQueueCapacity(normalizeQueueCapacity());
        executor.setThreadNamePrefix(normalizeThreadNamePrefix());
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    } else {
                        MDC.clear();
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(normalizeAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    private int normalizeCorePoolSize() {
        Integer configured = ragExecutorProperties.getCorePoolSize();
        return configured == null || configured < 1 ? 4 : configured;
    }

    private int normalizeMaxPoolSize() {
        Integer configured = ragExecutorProperties.getMaxPoolSize();
        int minValue = normalizeCorePoolSize();
        if (configured == null || configured < minValue) {
            return Math.max(minValue, 8);
        }
        return configured;
    }

    private int normalizeQueueCapacity() {
        Integer configured = ragExecutorProperties.getQueueCapacity();
        return configured == null || configured < 1 ? 100 : configured;
    }

    private int normalizeAwaitTerminationSeconds() {
        Integer configured = ragExecutorProperties.getAwaitTerminationSeconds();
        return configured == null || configured < 1 ? 30 : configured;
    }

    private String normalizeThreadNamePrefix() {
        String configured = ragExecutorProperties.getThreadNamePrefix();
        return configured == null || configured.isBlank() ? "rag-indexing-" : configured.trim();
    }
}
