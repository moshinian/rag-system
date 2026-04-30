package com.example.rag.integration.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容接口客户端占位实现。
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    /** 记录占位初始化日志。 */
    public void logPlaceholder() {
        log.debug("OpenAI compatible client placeholder initialized");
    }
}
