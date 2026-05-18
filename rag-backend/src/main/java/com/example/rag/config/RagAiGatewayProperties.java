package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Gateway 配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.ai.gateway")
public class RagAiGatewayProperties {
    private String baseUrl = "http://localhost:8001";
    private String embeddingsPath = "/v1/embeddings";
    private String chatCompletionsPath = "/v1/chat/completions";
    private Integer connectTimeoutMillis = 5_000;
    private Integer readTimeoutMillis = 30_000;
}
