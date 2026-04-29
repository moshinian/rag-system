package com.example.rag.integration.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    public void logPlaceholder() {
        log.debug("OpenAI compatible client placeholder initialized");
    }
}
