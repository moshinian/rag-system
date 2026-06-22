package com.example.rag.model.dto;

import java.util.Map;

/**
 * Agent 工具执行上下文。
 */
public record AgentToolContext(
        String kbCode,
        String question,
        String runCode,
        String operator,
        Map<String, Object> attributes
) {
    /** 使用知识库编码创建最小工具上下文。 */
    public static AgentToolContext forKnowledgeBase(String kbCode) {
        return new AgentToolContext(kbCode, null, null, null, Map.of());
    }
}
