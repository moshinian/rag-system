package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentRunMode;

/**
 * Java 调用 Python Agent Runtime 的请求契约。
 */
public record AgentRuntimeRequest(
        String runCode,
        String kbCode,
        String goal,
        String question,
        AgentRunMode runMode
) {
}
