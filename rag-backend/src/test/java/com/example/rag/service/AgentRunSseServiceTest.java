package com.example.rag.service;

import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent SSE 订阅历史补发测试。 */
class AgentRunSseServiceTest {

    @Test
    void subscribeShouldValidateOwnershipAndQueryFromLastEventId() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        AgentRunEntity run = new AgentRunEntity();
        run.setKnowledgeBaseId(1L);
        run.setRunCode("AR-1");
        when(knowledgeBaseRepository.findByCode("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));
        when(eventService.findEventsAfter("AR-1", "EVT-1")).thenReturn(List.of());
        AgentRunSseService service = new AgentRunSseService(
                knowledgeBaseRepository,
                runRepository,
                eventService
        );

        SseEmitter emitter = service.subscribe("kb-1", "AR-1", "EVT-1");

        assertThat(emitter).isNotNull();
        verify(eventService).findEventsAfter("AR-1", "EVT-1");
    }

    @Test
    void subscribeShouldReplayTerminalHistory() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        AgentRunEntity run = new AgentRunEntity();
        run.setKnowledgeBaseId(1L);
        run.setRunCode("AR-1");
        when(knowledgeBaseRepository.findByCode("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));
        when(eventService.findEventsAfter("AR-1", null)).thenReturn(List.of(new AgentRunEventResponse(
                10L,
                "EVT-10",
                "AR-1",
                AgentRunEventType.RUN_COMPLETED,
                null,
                null,
                null,
                "SUCCEEDED",
                "done",
                "{}",
                true,
                OffsetDateTime.now()
        )));
        AgentRunSseService service = new AgentRunSseService(
                knowledgeBaseRepository,
                runRepository,
                eventService
        );

        assertThat(service.subscribe("kb-1", "AR-1", null)).isNotNull();
    }

    @Test
    void publishTerminalEventShouldCloseEmitterAndCleanupRunChannel() throws Exception {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        AgentRunEntity run = new AgentRunEntity();
        run.setKnowledgeBaseId(1L);
        run.setRunCode("AR-1");
        when(knowledgeBaseRepository.findByCode("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));
        when(eventService.findEventsAfter("AR-1", null)).thenReturn(List.of());
        AgentRunSseService service = new AgentRunSseService(
                knowledgeBaseRepository,
                runRepository,
                eventService
        );

        service.subscribe("kb-1", "AR-1", null);
        assertThat(activeChannelCount(service)).isEqualTo(1);

        service.publish(new AgentRunEventResponse(
                11L,
                "EVT-11",
                "AR-1",
                AgentRunEventType.RUN_FAILED,
                null,
                null,
                null,
                "FAILED",
                "failed",
                "{}",
                true,
                OffsetDateTime.now()
        ));

        assertThat(activeChannelCount(service)).isZero();
    }

    private int activeChannelCount(AgentRunSseService service) throws Exception {
        Field channelsField = AgentRunSseService.class.getDeclaredField("channels");
        channelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> channels = (Map<String, ?>) channelsField.get(service);
        return channels.size();
    }
}
