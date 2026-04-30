package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.storage")
public class RagStorageProperties {

    private String baseDir = "./data/uploads";
    private long maxFileSizeMb = 20;
}
