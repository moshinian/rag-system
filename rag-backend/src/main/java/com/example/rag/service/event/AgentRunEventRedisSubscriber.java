package com.example.rag.service.event;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.service.AgentRunEventService;
import com.example.rag.service.AgentRunSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 每个 Pod 订阅 Redis，并只向本 Pod 持有的浏览器连接发送事件。 */
@Component
public class AgentRunEventRedisSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(AgentRunEventRedisSubscriber.class);
    private final AgentRunEventService eventService;
    private final AgentRunSseService sseService;

    public AgentRunEventRedisSubscriber(AgentRunEventService eventService,
                                        AgentRunSseService sseService) {
        this.eventService = eventService;
        this.sseService = sseService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String value = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            long databaseId = Long.parseLong(value);
            eventService.findEventByDatabaseId(databaseId).ifPresent(sseService::publish);
        } catch (RuntimeException ex) {
            log.warn(StructuredLogMessage.of("agent.event.redis_message_ignored")
                    .field("payload", value)
                    .field("message", ex.getMessage())
                    .build());
        }
    }
}
