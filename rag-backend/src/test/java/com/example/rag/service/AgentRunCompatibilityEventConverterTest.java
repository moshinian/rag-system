package com.example.rag.service;

import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 旧 JSON 结果到前端事件的兼容转换测试。 */
class AgentRunCompatibilityEventConverterTest {

    @ParameterizedTest
    @CsvSource({
            "SUCCEEDED,RUN_COMPLETED",
            "FAILED,RUN_FAILED",
            "WAITING_CONFIRMATION,RUN_WAITING_CONFIRMATION"
    })
    void terminalEventShouldFollowJavaRunStatus(AgentRunStatus runStatus,
                                                AgentRunEventType expectedEventType) {
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentStepRepository stepRepository = mock(AgentStepRepository.class);
        AgentActionRepository actionRepository = mock(AgentActionRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        AgentRunEntity run = new AgentRunEntity();
        run.setRunCode("AR-1");
        run.setStatus(runStatus);
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));
        when(stepRepository.findByRunCode("AR-1")).thenReturn(List.of());
        when(actionRepository.findByRunCode("AR-1")).thenReturn(List.of());

        AgentRunCompatibilityEventConverter converter = new AgentRunCompatibilityEventConverter(
                runRepository,
                stepRepository,
                actionRepository,
                eventService,
                new ObjectMapper().findAndRegisterModules()
        );

        converter.publishPersistedResult("AR-1");

        ArgumentCaptor<AgentRunEventDraft> captor = ArgumentCaptor.forClass(AgentRunEventDraft.class);
        verify(eventService).persist(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(expectedEventType);
        assertThat(captor.getValue().eventCode()).isEqualTo("AR-1-TERMINAL");
    }
}
