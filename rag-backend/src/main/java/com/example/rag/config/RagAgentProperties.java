package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 内部调用配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.agent")
public class RagAgentProperties {
    private String internalToolToken = "dev-agent-tool-token";
}
