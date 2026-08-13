package com.example.rag.service.event;

/** Agent Event 跨实例实时通知的 Redis channel。 */
public final class AgentEventRedisChannel {
    public static final String NAME = "rag:agent-events:v1";

    private AgentEventRedisChannel() {
    }
}
