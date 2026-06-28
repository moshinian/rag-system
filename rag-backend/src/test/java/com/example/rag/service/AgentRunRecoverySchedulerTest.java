package com.example.rag.service;

import com.example.rag.config.RagAgentProperties;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunRecoveryScheduler scheduler = new AgentRunRecoveryScheduler(
                properties,
                runRepository,
                mock(AgentRunRecoveryService.class)
        );

        scheduler.recoverStaleRuns();

        verify(runRepository, never()).findRecoverableRunningRuns(any(), any(), any(Integer.class));
    }

    @Test
    void schedulerShouldRecoverEachCandidateIndependently() {
        RagAgentProperties properties = new RagAgentProperties();
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunRecoveryService recoveryService = mock(AgentRunRecoveryService.class);
        AgentRunEntity run = new AgentRunEntity();
        run.setRunCode("AR-1");
        when(runRepository.findRecoverableRunningRuns(any(), any(), eq(100))).thenReturn(List.of(run));
        AgentRunRecoveryScheduler scheduler = new AgentRunRecoveryScheduler(
                properties,
                runRepository,
                recoveryService
        );

        scheduler.recoverStaleRuns();

        verify(recoveryService).recoverOne(eq(run), any());
    }
}
