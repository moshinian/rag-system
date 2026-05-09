package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索链路配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.retrieval")
public class RagRetrievalProperties {

    private String vectorStore = "pgvector";
    private Integer defaultTopK = 5;
    private Integer maxTopK = 10;
    private Integer maxContextChars = 6000;
}
