package com.example.rag.service;

import com.example.rag.integration.agent.AgentRuntimeStreamingClient;
import com.example.rag.model.dto.AgentRuntimeEvent;
import com.example.rag.model.enums.AgentRunMode;
import com.example.rag.model.enums.AgentRuntimeEventType;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Agent streaming 后台执行器测试。 */
class AgentRunExecutorTest {
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    @Test
    void executorShouldApplyEventsAndAcceptTerminal() {
        AgentRuntimeStreamingClient client = mock(AgentRuntimeStreamingClient.class);
        AgentRunEventApplier applier = mock(AgentRunEventApplier.class);
        AgentRunHeartbeatService heartbeatService = mock(AgentRunHeartbeatService.class);
        AgentRuntimeEvent terminal = terminalEvent();
        doAnswer(invocation -> {
            java.util.function.Consumer<AgentRuntimeEvent> consumer = invocation.getArgument(1);
            consumer.accept(terminal);
            return null;
        }).when(client).runStream(any(), any(), any());
        org.mockito.Mockito.when(applier.apply(terminal)).thenReturn(true);
        AgentRunExecutor executor = new AgentRunExecutor(DIRECT_EXECUTOR, client, applier, heartbeatService);

        executor.submit("kb-1", run());

        verify(applier).apply(terminal);
        verify(heartbeatService).touchRuntimeHeartbeat("AR-1");
        verify(heartbeatService, times(2)).cleanup("AR-1");
        verify(applier, never()).markStreamFailed(any(), any());
    }

    @Test
    void executorShouldFailRunWhenStreamEndsWithoutTerminal() {
        AgentRuntimeStreamingClient client = mock(AgentRuntimeStreamingClient.class);
        AgentRunEventApplier applier = mock(AgentRunEventApplier.class);
        AgentRunHeartbeatService heartbeatService = mock(AgentRunHeartbeatService.class);
        AgentRunExecutor executor = new AgentRunExecutor(DIRECT_EXECUTOR, client, applier, heartbeatService);

        executor.submit("kb-1", run());

        verify(applier).markStreamFailed(
                "AR-1",
                "Python stream ended without terminal event"
        );
    }

    @Test
    void executorShouldFailRunWhenPythonStreamIsInterrupted() {
        AgentRuntimeStreamingClient client = mock(AgentRuntimeStreamingClient.class);
        AgentRunEventApplier applier = mock(AgentRunEventApplier.class);
        AgentRunHeartbeatService heartbeatService = mock(AgentRunHeartbeatService.class);
        doThrow(new RuntimeException("socket closed")).when(client).runStream(any(), any(), any());
        AgentRunExecutor executor = new AgentRunExecutor(DIRECT_EXECUTOR, client, applier, heartbeatService);

        executor.submit("kb-1", run());

        verify(applier).markStreamFailed(
                "AR-1",
                "Python stream interrupted: socket closed"
        );
    }

    @Test
    void executorShouldOnlyLogWhenFailurePersistenceAlsoFails() {
        AgentRuntimeStreamingClient client = mock(AgentRuntimeStreamingClient.class);
        AgentRunEventApplier applier = mock(AgentRunEventApplier.class);
        AgentRunHeartbeatService heartbeatService = mock(AgentRunHeartbeatService.class);
        doThrow(new RuntimeException("database unavailable"))
                .when(applier)
                .markStreamFailed(eq("AR-1"), any());
        AgentRunExecutor executor = new AgentRunExecutor(DIRECT_EXECUTOR, client, applier, heartbeatService);

        executor.submit("kb-1", run());

        verify(applier).markStreamFailed(
                "AR-1",
                "Python stream ended without terminal event"
        );
    }

    private AgentRunEntity run() {
        AgentRunEntity run = new AgentRunEntity();
        run.setRunCode("AR-1");
        run.setGoal("诊断");
        run.setRunMode(AgentRunMode.DIAGNOSE_ONLY);
        return run;
    }

    private AgentRuntimeEvent terminalEvent() {
        return new AgentRuntimeEvent(
                "EVT-terminal",
                "AR-1",
                AgentRuntimeEventType.RUN_COMPLETED,
                null,
                null,
                null,
                "SUCCEEDED",
                "done",
                new ObjectMapper().createObjectNode(),
                true,
                OffsetDateTime.now()
        );
    }
}
