package com.example.rag.config;

import com.example.rag.model.enums.KeywordStrategy;
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
    private KeywordStrategy keywordStrategy = KeywordStrategy.LIKE;
    private String keywordTsConfig = "simple";
    private String keywordRankFunction = "ts_rank_cd";
    private Integer keywordMinTokenLength = 3;
    private Double keywordLikePhraseWeight = 3D;
    private Double keywordLikeTitleWeight = 1.5D;
    private Double keywordMinHitThreshold = 0.5D;
    private Integer maxContextChars = 6000;
}
