package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.integration.agent.AgentRuntimeStreamingClient;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在独立线程池中消费 Python Agent Runtime SSE。
 */
@Component
public class AgentRunExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentRunExecutor.class);

    private final Executor executor;
    private final AgentRuntimeStreamingClient streamingClient;
    private final AgentRunEventApplier eventApplier;
    private final AgentRunHeartbeatService heartbeatService;

    /** 构造 AgentRunExecutor。 */
    public AgentRunExecutor(@Qualifier("agentExecutor") Executor executor,
                            AgentRuntimeStreamingClient streamingClient,
                            AgentRunEventApplier eventApplier,
                            AgentRunHeartbeatService heartbeatService) {
        this.executor = executor;
        this.streamingClient = streamingClient;
        this.eventApplier = eventApplier;
        this.heartbeatService = heartbeatService;
    }

    /**
     * 提交后台任务。
     *
     * <p>队列拒绝异常会同步抛给调用方，由 AgentRunService 将 run 收口为 FAILED。</p>
     */
    public void submit(String kbCode, AgentRunEntity run) {
        executor.execute(() -> execute(kbCode, run));
    }

    /** 消费 Python Runtime SSE，逐条事务化落库并推送前端。 */
    private void execute(String kbCode, AgentRunEntity run) {
        log.info(StructuredLogMessage.of("agent.run.background.started")
                .field("runCode", run.getRunCode())
                .field("kbCode", kbCode)
                .build());
        AtomicBoolean receivedTerminal = new AtomicBoolean(false);
        try {
            streamingClient.runStream(new AgentRuntimeRequest(
                    run.getRunCode(),
                    kbCode,
                    run.getGoal(),
                    run.getQuestion(),
                    run.getRunMode()
            ), event -> {
                heartbeatService.touchRuntimeHeartbeat(event.runCode());
                boolean inserted = eventApplier.apply(event);
                if (inserted && event.terminal()) {
                    receivedTerminal.set(true);
                    heartbeatService.cleanup(event.runCode());
                }
            }, () -> heartbeatService.touchRuntimeHeartbeat(run.getRunCode()));
            if (!receivedTerminal.get()) {
                eventApplier.markStreamFailed(
                        run.getRunCode(),
                        "Python stream ended without terminal event"
                );
            }
        } catch (Exception ex) {
            log.warn(StructuredLogMessage.of("agent.run.background.failed")
                    .field("runCode", run.getRunCode())
                    .field("message", ex.getMessage())
                    .build());
            safelyMarkFailed(
                    run.getRunCode(),
                    "Python stream interrupted: " + ex.getMessage()
            );
        } finally {
            heartbeatService.cleanup(run.getRunCode());
        }

        log.info(StructuredLogMessage.of("agent.run.background.completed")
                .field("runCode", run.getRunCode())
                .build());
    }

    /** 数据库也不可用时只记录日志，避免后台线程继续抛出未处理异常。 */
    private void safelyMarkFailed(String runCode, String errorMessage) {
        try {
            eventApplier.markStreamFailed(runCode, errorMessage);
        } catch (Exception persistenceError) {
            log.error(StructuredLogMessage.of("agent.run.failure_persist_failed")
                    .field("runCode", runCode)
                    .field("message", persistenceError.getMessage())
                    .build(), persistenceError);
        }
    }
}
