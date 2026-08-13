package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.integration.agent.AgentRuntimeStreamingClient;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.example.rag.config.RagAgentProperties;

/**
 * 在独立线程池中消费 Python Agent Runtime SSE。
 */
@Component
public class AgentRunExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentRunExecutor.class);

    private final AgentRuntimeStreamingClient streamingClient;
    private final AgentRunEventApplier eventApplier;
    private final AgentRunHeartbeatService heartbeatService;
    private final RagAgentProperties properties;
    private final ScheduledExecutorService heartbeatExecutor;

    /** 构造 AgentRunExecutor。 */
    public AgentRunExecutor(AgentRuntimeStreamingClient streamingClient,
                            AgentRunEventApplier eventApplier,
                            AgentRunHeartbeatService heartbeatService,
                            RagAgentProperties properties,
                            ScheduledExecutorService heartbeatExecutor) {
        this.streamingClient = streamingClient;
        this.eventApplier = eventApplier;
        this.heartbeatService = heartbeatService;
        this.properties = properties;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    /** 消费 Python Runtime SSE，逐条事务化落库并推送前端。 */
    public void executeClaimed(String kbCode, AgentRunEntity run) {
        log.info(StructuredLogMessage.of("agent.run.background.started")
                .field("runCode", run.getRunCode())
                .field("kbCode", kbCode)
                .build());
        AtomicBoolean receivedTerminal = new AtomicBoolean(false);
        AtomicBoolean ownsLease = new AtomicBoolean(true);
        long heartbeatMillis = properties.getWorker().getHeartbeatInterval().toMillis();
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (ownsLease.get() && !heartbeatService.touchOwnedHeartbeat(run)) {
                    ownsLease.set(false);
                }
            } catch (RuntimeException ex) {
                log.warn(StructuredLogMessage.of("agent.run.heartbeat_failed")
                        .field("runCode", run.getRunCode())
                        .field("instanceId", run.getOwnerInstanceId())
                        .field("message", ex.getMessage())
                        .build());
            }
        }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            streamingClient.runStream(new AgentRuntimeRequest(
                    run.getRunCode(),
                    kbCode,
                    run.getGoal(),
                    run.getQuestion()
            ), event -> {
                if (!ownsLease.get()) {
                    throw new IllegalStateException("Agent run ownership lost: " + run.getRunCode());
                }
                boolean inserted = eventApplier.applyOwned(event, run.getOwnerInstanceId(), run.getLeaseVersion());
                if (inserted && event.terminal()) {
                    receivedTerminal.set(true);
                    heartbeatService.cleanup(event.runCode());
                }
            }, () -> {
                if (!heartbeatService.touchOwnedHeartbeat(run)) {
                    ownsLease.set(false);
                }
            });
            if (!receivedTerminal.get()) {
                eventApplier.markStreamFailedOwned(run.getRunCode(), run.getOwnerInstanceId(),
                        run.getLeaseVersion(), "Python stream ended without terminal event");
            }
        } catch (Exception ex) {
            log.warn(StructuredLogMessage.of("agent.run.background.failed")
                    .field("runCode", run.getRunCode())
                    .field("message", ex.getMessage())
                    .build());
            safelyMarkFailed(run, "Python stream interrupted: " + ex.getMessage());
        } finally {
            heartbeat.cancel(false);
            heartbeatService.cleanup(run.getRunCode());
        }

        log.info(StructuredLogMessage.of("agent.run.background.completed")
                .field("runCode", run.getRunCode())
                .build());
    }

    /** 数据库也不可用时只记录日志，避免后台线程继续抛出未处理异常。 */
    private void safelyMarkFailed(AgentRunEntity run, String errorMessage) {
        try {
            eventApplier.markStreamFailedOwned(run.getRunCode(), run.getOwnerInstanceId(),
                    run.getLeaseVersion(), errorMessage);
        } catch (Exception persistenceError) {
            log.error(StructuredLogMessage.of("agent.run.failure_persist_failed")
                    .field("runCode", run.getRunCode())
                    .field("message", persistenceError.getMessage())
                    .build(), persistenceError);
        }
    }
}
