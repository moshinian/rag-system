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
    private final RagAgentProperties ragAgentProperties;

    /** 构造ExecutorConfig。 */
    public ExecutorConfig(RagExecutorProperties ragExecutorProperties,
                          RagAgentProperties ragAgentProperties) {
        this.ragExecutorProperties = ragExecutorProperties;
        this.ragAgentProperties = ragAgentProperties;
    }

    /** 定义索引处理线程池。 */
    @Bean("indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizeCorePoolSize());
        executor.setMaxPoolSize(normalizeMaxPoolSize());
        executor.setQueueCapacity(normalizeQueueCapacity());
        executor.setThreadNamePrefix(normalizeThreadNamePrefix());
        executor.setTaskDecorator(this::decorateWithMdc);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(normalizeAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    /** 定义 Agent run 后台执行线程池。 */
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        RagAgentProperties.Executor properties = ragAgentProperties.getExecutor();
        int corePoolSize = normalizePositive(properties.getCorePoolSize(), 2);
        int maxPoolSize = Math.max(corePoolSize, normalizePositive(properties.getMaxPoolSize(), 4));

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(normalizePositive(properties.getQueueCapacity(), 100));
        executor.setThreadNamePrefix(normalizeText(properties.getThreadNamePrefix(), "rag-agent-"));
        executor.setTaskDecorator(this::decorateWithMdc);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(normalizePositive(properties.getAwaitTerminationSeconds(), 30));
        executor.initialize();
        return executor;
    }

    /** 归一化核心线程数，非法配置回退为 4。 */
    private int normalizeCorePoolSize() {
        Integer configured = ragExecutorProperties.getCorePoolSize();
        return configured == null || configured < 1 ? 4 : configured;
    }

    /** 归一化最大线程数，并保证不小于核心线程数。 */
    private int normalizeMaxPoolSize() {
        Integer configured = ragExecutorProperties.getMaxPoolSize();
        int minValue = normalizeCorePoolSize();
        if (configured == null || configured < minValue) {
            return Math.max(minValue, 8);
        }
        return configured;
    }

    /** 归一化任务队列容量，非法配置回退为 100。 */
    private int normalizeQueueCapacity() {
        Integer configured = ragExecutorProperties.getQueueCapacity();
        return configured == null || configured < 1 ? 100 : configured;
    }

    /** 归一化停机等待秒数，非法配置回退为 30 秒。 */
    private int normalizeAwaitTerminationSeconds() {
        Integer configured = ragExecutorProperties.getAwaitTerminationSeconds();
        return configured == null || configured < 1 ? 30 : configured;
    }

    /** 归一化线程名前缀，便于从日志识别索引工作线程。 */
    private String normalizeThreadNamePrefix() {
        String configured = ragExecutorProperties.getThreadNamePrefix();
        return configured == null || configured.isBlank() ? "rag-indexing-" : configured.trim();
    }

    /** 给异步任务复制并恢复 MDC，保证跨线程日志仍可关联 requestId。 */
    private Runnable decorateWithMdc(Runnable runnable) {
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
    }

    /** 把非正整数配置回退到默认值。 */
    private int normalizePositive(Integer configured, int fallback) {
        return configured == null || configured < 1 ? fallback : configured;
    }

    /** 把空白文本配置回退到默认值。 */
    private String normalizeText(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured.trim();
    }
}
