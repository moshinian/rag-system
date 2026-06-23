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
}
