package com.example.rag.service;

import com.example.rag.config.RagAgentProperties;
import com.example.rag.persistence.AgentRunRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent Runtime heartbeat 持久化测试。 */
class AgentRunHeartbeatServiceTest {

    @Test
    void touchShouldThrottleDatabaseUpdatesAndCleanupShouldAllowNextUpdate() {
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        when(runRepository.updateRuntimeHeartbeatToNow("AR-1")).thenReturn(1);
        RagAgentProperties properties = new RagAgentProperties();
        properties.getRecovery().setHeartbeatUpdateIntervalSeconds(30);
        AgentRunHeartbeatService service = new AgentRunHeartbeatService(runRepository, properties);

        service.touchRuntimeHeartbeat("AR-1");
        service.touchRuntimeHeartbeat("AR-1");
        service.cleanup("AR-1");
        service.touchRuntimeHeartbeat("AR-1");

        verify(runRepository, times(2)).updateRuntimeHeartbeatToNow("AR-1");
    }

    @Test
    void touchShouldNotInterruptWhenRepositoryFails() {
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        when(runRepository.updateRuntimeHeartbeatToNow("AR-1"))
                .thenThrow(new IllegalStateException("database unavailable"));
        AgentRunHeartbeatService service = new AgentRunHeartbeatService(runRepository, new RagAgentProperties());

        service.touchRuntimeHeartbeat("AR-1");

        verify(runRepository).updateRuntimeHeartbeatToNow("AR-1");
    }
}
