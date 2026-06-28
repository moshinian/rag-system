package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentRuntimeEventType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * Python Runtime 通过内部 SSE 发送给 Java 的事件。
 */
public record AgentRuntimeEvent(
        String eventId,
        String runCode,
        AgentRuntimeEventType type,
        String nodeInvocationId,
        String nodeName,
        String toolName,
        String status,
        String message,
        JsonNode payload,
        boolean terminal,
        OffsetDateTime createdAt
) {
}
