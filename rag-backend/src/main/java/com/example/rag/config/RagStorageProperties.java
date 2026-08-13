package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.storage")
public class RagStorageProperties {
    private String type = "local";
    private String baseDir = "./data/uploads";
    private long maxFileSizeMb = 20;
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String bucket = "rag-files";
        private String accessKey = "rag_minio";
        private String secretKey = "rag_minio_password";
    }
}
