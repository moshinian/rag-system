package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 模型配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.embedding")
public class RagEmbeddingProperties {

    private String provider = "openai-compatible";
    private String model = "text-embedding-3-small";
}
