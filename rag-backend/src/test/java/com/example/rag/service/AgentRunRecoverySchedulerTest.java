package com.example.rag.service;

import com.example.rag.config.RagAgentProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent run Recovery 调度器测试。 */
class AgentRunRecoverySchedulerTest {

    @Test
    void schedulerShouldSkipWhenDisabled() {
        RagAgentProperties properties = new RagAgentProperties();
        properties.getRecovery().setEnabled(false);
        AgentRunLeaseRecoveryCoordinator coordinator = mock(AgentRunLeaseRecoveryCoordinator.class);
        AgentRunRecoveryScheduler scheduler = new AgentRunRecoveryScheduler(
                properties,
                coordinator
        );

        scheduler.recoverStaleRuns();

        verify(coordinator, never()).recoverOne();
    }

    @Test
    void schedulerShouldRecoverEachCandidateIndependently() {
        RagAgentProperties properties = new RagAgentProperties();
        AgentRunLeaseRecoveryCoordinator coordinator = mock(AgentRunLeaseRecoveryCoordinator.class);
        when(coordinator.recoverOne()).thenReturn(Optional.of("AR-1"), Optional.empty());
        AgentRunRecoveryScheduler scheduler = new AgentRunRecoveryScheduler(
                properties,
                coordinator
        );

        scheduler.recoverStaleRuns();

        verify(coordinator, org.mockito.Mockito.times(2)).recoverOne();
    }
}
