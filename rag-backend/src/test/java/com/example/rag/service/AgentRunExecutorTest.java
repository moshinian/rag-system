package com.example.rag.service;

import com.example.rag.integration.agent.AgentRuntimeStreamingClient;
import com.example.rag.config.RagAgentProperties;
import com.example.rag.model.dto.AgentRuntimeEvent;
import com.example.rag.model.enums.AgentRuntimeEventType;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Agent streaming 后台执行器测试。 */
class AgentRunExecutorTest {
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
        org.mockito.Mockito.when(applier.applyOwned(terminal, "pod-a", 1L)).thenReturn(true);
        AgentRunExecutor executor = executor(client, applier, heartbeatService);

        executor.executeClaimed("kb-1", run());

        verify(applier).applyOwned(terminal, "pod-a", 1L);
        verify(heartbeatService, times(2)).cleanup("AR-1");
        verify(applier, never()).markStreamFailed(any(), any());
    }

    @Test
    void executorShouldFailRunWhenStreamEndsWithoutTerminal() {
        AgentRuntimeStreamingClient client = mock(AgentRuntimeStreamingClient.class);
        AgentRunEventApplier applier = mock(AgentRunEventApplier.class);
        AgentRunHeartbeatService heartbeatService = mock(AgentRunHeartbeatService.class);
        AgentRunExecutor executor = executor(client, applier, heartbeatService);

        executor.executeClaimed("kb-1", run());

        verify(applier).markStreamFailedOwned(
                "AR-1", "pod-a", 1L,
                "Python stream ended without terminal event"
        );
    }

    @Test
    void executorShouldFailRunWhenPythonStreamIsInterrupted() {
        AgentRuntimeStreamingClient client = mock(AgentRuntimeStreamingClient.class);
        AgentRunEventApplier applier = mock(AgentRunEventApplier.class);
        AgentRunHeartbeatService heartbeatService = mock(AgentRunHeartbeatService.class);
        doThrow(new RuntimeException("socket closed")).when(client).runStream(any(), any(), any());
        AgentRunExecutor executor = executor(client, applier, heartbeatService);

        executor.executeClaimed("kb-1", run());

        verify(applier).markStreamFailedOwned(
                "AR-1", "pod-a", 1L,
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
                .markStreamFailedOwned(eq("AR-1"), eq("pod-a"), eq(1L), any());
        AgentRunExecutor executor = executor(client, applier, heartbeatService);

        executor.executeClaimed("kb-1", run());

        verify(applier).markStreamFailedOwned(
                "AR-1", "pod-a", 1L,
                "Python stream ended without terminal event"
        );
    }

    private AgentRunEntity run() {
        AgentRunEntity run = new AgentRunEntity();
        run.setRunCode("AR-1");
        run.setGoal("诊断");
        run.setOwnerInstanceId("pod-a");
        run.setLeaseVersion(1L);
        return run;
    }

    private AgentRunExecutor executor(AgentRuntimeStreamingClient client,
                                      AgentRunEventApplier applier,
                                      AgentRunHeartbeatService heartbeatService) {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        return new AgentRunExecutor(client, applier, heartbeatService,
                new RagAgentProperties(), scheduler);
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
