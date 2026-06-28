package com.example.rag.model.response;

import com.example.rag.model.enums.AgentRunEventType;

import java.time.OffsetDateTime;

/**
 * Java SSE 返回给 React 的规范化 Agent 事件。
 */
public record AgentRunEventResponse(
        Long databaseId,
        String eventId,
        String runCode,
        AgentRunEventType type,
        String nodeInvocationId,
        String nodeName,
        String toolName,
        String status,
        String message,
        String payloadJson,
        boolean terminal,
        OffsetDateTime createdAt
) {
}
