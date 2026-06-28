package com.example.rag.service.event;

import com.example.rag.model.response.AgentRunEventResponse;

/**
 * Agent 事件事务提交完成后的进程内通知。
 */
public record AgentRunEventCommittedEvent(AgentRunEventResponse event) {
}
