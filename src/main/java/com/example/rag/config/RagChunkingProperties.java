package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档切块配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.chunking")
public class RagChunkingProperties {

    private String strategy = "fixed-window";

    private Integer maxChunkChars = 600;

    private Integer overlapChars = 80;

    private Integer minBreakSearchOffset = 240;
}
