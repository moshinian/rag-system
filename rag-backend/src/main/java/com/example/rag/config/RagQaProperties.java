package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问答记录相关配置。
 */
@Data
@ConfigurationProperties(prefix = "rag.qa")
public class RagQaProperties {

    private String defaultCreatedBy = "qa-service";

    private String messageType = "QA";

    private String promptTemplate = "qa-default-v1";

    private Integer sessionNameMaxLength = 80;
}
