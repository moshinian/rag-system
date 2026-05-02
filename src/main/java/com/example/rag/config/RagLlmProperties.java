package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 大模型调用配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.llm")
public class RagLlmProperties {

    private String baseUrl = "http://localhost:8000/v1";
    private String apiKey = "change-me";
    private String chatModel = "gpt-4o-mini";
    private String chatCompletionPath = "/chat/completions";
    private Double temperature = 0.2D;
    private Integer maxOutputTokens = 1200;
}
