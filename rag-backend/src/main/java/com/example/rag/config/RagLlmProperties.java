package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 大模型调用配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.llm")
public class RagLlmProperties {

    private ChatProperties chat = new ChatProperties();

    @Data
    public static class ChatProperties {

        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "${DEEPSEEK_API_KEY}";
        private String model = "deepseek-v4-pro";
        private String chatPath = "/chat/completions";
        private Double temperature = 0.2D;
        private Integer maxOutputTokens = 1200;
    }
}
