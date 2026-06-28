package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.persistence.AgentRunEventRepository;
import com.example.rag.persistence.entity.AgentRunEventEntity;
import com.example.rag.service.event.AgentRunEventCommittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent 事件持久化与补发查询测试。 */
@ExtendWith(MockitoExtension.class)
class AgentRunEventServiceTest {
    @Mock
    private AgentRunEventRepository eventRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    @SuppressWarnings("null") // Mockito capture/any 以 null matcher 工作，测试断言会在捕获后校验真实事件。
    void persistShouldPublishCommittedEventOnlyWhenInsertSucceeds() {
        AgentRunEventService service = service();
        when(snowflakeIdGenerator.nextId()).thenReturn(101L);
        when(eventRepository.insertIgnore(any())).thenReturn(true);

        Optional<AgentRunEventResponse> result = service.persist(draft("EVT-fixed"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().databaseId()).isEqualTo(101L);
        assertThat(result.orElseThrow().eventId()).isEqualTo("EVT-fixed");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOfSatisfying(AgentRunEventCommittedEvent.class,
                        event -> assertThat(event.event().type()).isEqualTo(AgentRunEventType.RUN_STARTED));
    }

    @Test
    @SuppressWarnings("null") // Mockito any(Object.class) 以 null matcher 表达任意参数。
    void persistShouldIgnoreDuplicateEventWithoutPublishing() {
        AgentRunEventService service = service();
        when(snowflakeIdGenerator.nextId()).thenReturn(101L);
        when(eventRepository.insertIgnore(any())).thenReturn(false);

        assertThat(service.persist(draft("EVT-fixed"))).isEmpty();

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void findEventsAfterShouldResolveDatabaseIdFromLastEventId() {
        AgentRunEventService service = service();
        AgentRunEventEntity last = entity(100L, "EVT-100", "AR-1");
        AgentRunEventEntity next = entity(101L, "EVT-101", "AR-1");
        when(eventRepository.findByEventCode("EVT-100")).thenReturn(Optional.of(last));
        when(eventRepository.findAfterId("AR-1", 100L)).thenReturn(List.of(next));

        List<AgentRunEventResponse> events = service.findEventsAfter("AR-1", "EVT-100");

        assertThat(events).extracting(event -> event.eventId()).containsExactly("EVT-101");
    }

    @Test
    void findEventsAfterShouldRejectLastEventFromAnotherRun() {
        AgentRunEventService service = service();
        when(eventRepository.findByEventCode("EVT-other"))
                .thenReturn(Optional.of(entity(100L, "EVT-other", "AR-other")));

        assertThatThrownBy(() -> service.findEventsAfter("AR-1", "EVT-other"))
                .hasMessageContaining("does not belong");
    }

    private AgentRunEventService service() {
        return new AgentRunEventService(
                eventRepository,
                snowflakeIdGenerator,
                applicationEventPublisher
        );
    }

    private AgentRunEventDraft draft(String eventCode) {
        return new AgentRunEventDraft(
                eventCode,
                "AR-1",
                null,
                AgentRunEventType.RUN_STARTED,
                null,
                null,
                "RUNNING",
                "started",
                "{}"
        );
    }

    private AgentRunEventEntity entity(Long id, String eventCode, String runCode) {
        AgentRunEventEntity entity = new AgentRunEventEntity();
        entity.setId(id);
        entity.setEventCode(eventCode);
        entity.setRunCode(runCode);
        entity.setEventType(AgentRunEventType.RUN_STARTED);
        entity.setTerminal(false);
        return entity;
    }
}
