package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 模型配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.embedding")
public class RagEmbeddingProperties {

    /**
     * 当前默认约定本地 embedding 服务暴露 OpenAI-compatible /embeddings 接口。
     */
    private String provider = "local-openai-compatible";
    private String baseUrl = "http://localhost:8001/v1";
    private String apiKey = "";
    private String model = "bge-small-zh-v1.5";
    private Integer vectorDimensions = 512;
    private String embeddingPath = "/embeddings";
    private Integer batchSize = 16;
}
