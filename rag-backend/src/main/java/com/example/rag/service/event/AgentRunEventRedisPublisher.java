package com.example.rag.service.event;

import com.example.rag.common.logging.StructuredLogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后广播数据库事件 ID；Redis 不承载事件正文和历史。 */
@Component
public class AgentRunEventRedisPublisher {
    private static final Logger log = LoggerFactory.getLogger(AgentRunEventRedisPublisher.class);
    private final StringRedisTemplate redis;

    public AgentRunEventRedisPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(AgentRunEventCommittedEvent event) {
        try {
            redis.convertAndSend(AgentEventRedisChannel.NAME,
                    String.valueOf(event.event().databaseId()));
        } catch (RuntimeException ex) {
            // Durable event 已在 PostgreSQL；实时通知失败由 Last-Event-ID 回放补偿。
            log.warn(StructuredLogMessage.of("agent.event.redis_publish_failed")
                    .field("runCode", event.event().runCode())
                    .field("databaseId", event.event().databaseId())
                    .field("message", ex.getMessage())
                    .build());
        }
    }
}
