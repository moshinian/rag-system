package com.example.rag.config;

import com.example.rag.model.enums.RetrievalMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索链路配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.retrieval")
public class RagRetrievalProperties {
    private String vectorStore = "pgvector";
    private RetrievalMode defaultMode = RetrievalMode.DENSE;
    private Integer defaultTopK = 5;
    private Integer maxTopK = 10;
    private Integer denseCandidateLimit = 8;
    private Integer keywordCandidateLimit = 8;
    private Integer fusionK = 60;
    private Integer keywordMinTokenLength = 3;
    private Integer maxContextChars = 6000;
}
