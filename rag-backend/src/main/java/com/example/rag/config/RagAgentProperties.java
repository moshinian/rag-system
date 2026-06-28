package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 内部调用配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.agent")
public class RagAgentProperties {
    private String internalToolToken = "dev-agent-tool-token";
    private List<String> mcpAllowedOrigins = List.of("http://127.0.0.1:8001", "http://localhost:8001");
    private Executor executor = new Executor();
    private Recovery recovery = new Recovery();

    /**
     * Agent 后台执行线程池配置。
     */
    @Data
    public static class Executor {
        private Integer corePoolSize = 2;
        private Integer maxPoolSize = 4;
        private Integer queueCapacity = 100;
        private Integer awaitTerminationSeconds = 30;
        private String threadNamePrefix = "rag-agent-";
    }

    /**
     * Agent run 恢复扫描配置。
     */
    @Data
    public static class Recovery {
        private boolean enabled = true;
        private long scanIntervalSeconds = 60;
        private long runningTimeoutMinutes = 10;
        private long idleTimeoutMinutes = 3;
        private long heartbeatUpdateIntervalSeconds = 30;
    }
}
