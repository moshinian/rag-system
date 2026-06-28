package com.example.rag.service.event;

import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.service.AgentRunSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/** 验证 Agent SSE 只在事务提交后推送，回滚时不推送。 */
@SpringBootTest(classes = AgentRunEventSseListenerTransactionTest.TestApplication.class)
class AgentRunEventSseListenerTransactionTest {

    @Autowired
    private TransactionalEventPublisher transactionalEventPublisher;

    @Autowired
    private AgentRunSseService sseService;

    @BeforeEach
    void resetMocks() {
        reset(sseService);
    }

    @Test
    void committedTransactionShouldPublishSseEventAfterCommit() {
        AgentRunEventResponse event = event("EVT-commit");

        transactionalEventPublisher.publishAndCommit(event);

        verify(sseService).publish(event);
    }

    @Test
    void rolledBackTransactionShouldNotPublishSseEvent() {
        AgentRunEventResponse event = event("EVT-rollback");

        assertThatThrownBy(() -> transactionalEventPublisher.publishAndRollback(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rollback");

        verify(sseService, never()).publish(any());
    }

    private AgentRunEventResponse event(String eventId) {
        return new AgentRunEventResponse(
                1L,
                eventId,
                "AR-1",
                AgentRunEventType.RUN_STARTED,
                null,
                null,
                null,
                "RUNNING",
                "started",
                "{}",
                false,
                OffsetDateTime.now()
        );
    }

    @SpringBootConfiguration
    @EnableTransactionManagement
    @Import({AgentRunEventSseListener.class, TransactionalEventPublisher.class})
    static class TestApplication {
        @Bean
        AgentRunSseService agentRunSseService() {
            return mock(AgentRunSseService.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                @NonNull
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(@NonNull Object transaction,
                                       @NonNull org.springframework.transaction.TransactionDefinition definition) {
                    // 测试用空事务管理器，只需要触发 Spring 事务同步回调。
                }

                @Override
                protected void doCommit(@NonNull DefaultTransactionStatus status) {
                    // 提交时由 Spring 触发 AFTER_COMMIT 事务事件监听器。
                }

                @Override
                protected void doRollback(@NonNull DefaultTransactionStatus status) {
                    // 回滚时不应触发 AFTER_COMMIT 事务事件监听器。
                }
            };
        }
    }

    static class TransactionalEventPublisher {
        private final ApplicationEventPublisher applicationEventPublisher;

        TransactionalEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
            this.applicationEventPublisher = applicationEventPublisher;
        }

        @Transactional
        void publishAndCommit(AgentRunEventResponse event) {
            applicationEventPublisher.publishEvent(new AgentRunEventCommittedEvent(event));
        }

        @Transactional
        void publishAndRollback(AgentRunEventResponse event) {
            applicationEventPublisher.publishEvent(new AgentRunEventCommittedEvent(event));
            throw new IllegalStateException("rollback");
        }
    }
}
