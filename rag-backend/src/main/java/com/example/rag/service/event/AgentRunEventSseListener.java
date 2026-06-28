package com.example.rag.service.event;

import com.example.rag.service.AgentRunSseService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后把 Agent 事件推送给在线 SSE 订阅者。
 */
@Component
public class AgentRunEventSseListener {
    private final AgentRunSseService sseService;

    /** 构造 AgentRunEventSseListener。 */
    public AgentRunEventSseListener(AgentRunSseService sseService) {
        this.sseService = sseService;
    }

    /** 只有数据库事务成功提交后才允许对外推送。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(AgentRunEventCommittedEvent committedEvent) {
        sseService.publish(committedEvent.event());
    }
}
