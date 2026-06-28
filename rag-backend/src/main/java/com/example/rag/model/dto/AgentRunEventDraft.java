package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentRunEventType;

/**
 * Java 侧待持久化的规范化 Agent 事件草案。
 */
public record AgentRunEventDraft(
        String eventCode,
        String runCode,
        String nodeInvocationId,
        AgentRunEventType eventType,
        String nodeName,
        String toolName,
        String status,
        String message,
        String payloadJson
) {
}
