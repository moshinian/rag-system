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
    private String rerankPath = "/v1/rerank";
    private String agentRunsPath = "/v1/agent/runs";
    private String agentRunsStreamPath = "/v1/agent/runs/stream";
    private Integer connectTimeoutMillis = 5_000;
    private Integer readTimeoutMillis = 30_000;
    private Integer rerankReadTimeoutMillis = 10_000;
    private Integer agentStreamReadTimeoutMillis = 120_000;
}
