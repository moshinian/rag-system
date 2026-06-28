package com.example.rag.controller;

import com.example.rag.service.AgentRunSseService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent SSE Controller 测试。 */
class AgentRunEventControllerTest {

    @Test
    void streamEventsShouldPassLastEventIdToService() {
        AgentRunSseService sseService = mock(AgentRunSseService.class);
        SseEmitter emitter = new SseEmitter(0L);
        when(sseService.subscribe("kb-1", "AR-1", "EVT-9")).thenReturn(emitter);
        AgentRunEventController controller = new AgentRunEventController(sseService);

        assertThat(controller.streamEvents("kb-1", "AR-1", "EVT-9")).isSameAs(emitter);
        verify(sseService).subscribe("kb-1", "AR-1", "EVT-9");
    }
}
