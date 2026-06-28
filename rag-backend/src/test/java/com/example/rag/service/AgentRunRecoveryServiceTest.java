package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent run Recovery 服务测试。 */
class AgentRunRecoveryServiceTest {

    @Test
    void recoverOneShouldUpdateRunAndPersistTerminalEventInTransactionalMethod() throws Exception {
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        OffsetDateTime idleCutoff = OffsetDateTime.parse("2026-06-24T12:00:00Z");
        when(runRepository.markRunningRunFailedByRecovery("AR-1", idleCutoff,
                AgentRunRecoveryService.RECOVERY_ERROR_MESSAGE)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(900L);
        when(eventService.persist(any())).thenReturn(Optional.of(response()));
        AgentRunRecoveryService service = new AgentRunRecoveryService(
                runRepository,
                eventService,
                idGenerator,
                new ObjectMapper().findAndRegisterModules()
        );

        boolean recovered = service.recoverOne(run(), idleCutoff);

        assertThat(recovered).isTrue();
        verify(eventService).persist(org.mockito.ArgumentMatchers.argThat(draft ->
                draft.eventCode().equals("AR-1-J-RECOVERY-900")
                        && draft.eventType() == AgentRunEventType.RUN_FAILED
                        && draft.payloadJson().contains("\"source\":\"JAVA_RECOVERY\"")
        ));
        Method method = AgentRunRecoveryService.class.getMethod(
                "recoverOne",
                AgentRunEntity.class,
                OffsetDateTime.class
        );
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void recoverOneShouldNotPersistEventWhenConditionalUpdateMissed() {
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        OffsetDateTime idleCutoff = OffsetDateTime.parse("2026-06-24T12:00:00Z");
        when(runRepository.markRunningRunFailedByRecovery(eq("AR-1"), eq(idleCutoff), any()))
                .thenReturn(0);
        AgentRunRecoveryService service = new AgentRunRecoveryService(
                runRepository,
                eventService,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper().findAndRegisterModules()
        );

        assertThat(service.recoverOne(run(), idleCutoff)).isFalse();

        verify(eventService, never()).persist(any());
    }

    @Test
    void recoverOneShouldThrowWhenEventPersistFailsSoTransactionCanRollbackRunUpdate() {
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        OffsetDateTime idleCutoff = OffsetDateTime.parse("2026-06-24T12:00:00Z");
        when(runRepository.markRunningRunFailedByRecovery(eq("AR-1"), eq(idleCutoff), any()))
                .thenReturn(1);
        when(idGenerator.nextId()).thenReturn(900L);
        when(eventService.persist(any(AgentRunEventDraft.class))).thenReturn(Optional.empty());
        AgentRunRecoveryService service = new AgentRunRecoveryService(
                runRepository,
                eventService,
                idGenerator,
                new ObjectMapper().findAndRegisterModules()
        );

        assertThatThrownBy(() -> service.recoverOne(run(), idleCutoff))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recovery event");
    }

    private AgentRunEntity run() {
        AgentRunEntity run = new AgentRunEntity();
        run.setRunCode("AR-1");
        run.setStatus(AgentRunStatus.RUNNING);
        return run;
    }

    private AgentRunEventResponse response() {
        return new AgentRunEventResponse(
                1L,
                "AR-1-J-RECOVERY-900",
                "AR-1",
                AgentRunEventType.RUN_FAILED,
                null,
                null,
                null,
                AgentRunStatus.FAILED.name(),
                AgentRunRecoveryService.RECOVERY_ERROR_MESSAGE,
                "{}",
                true,
                OffsetDateTime.now()
        );
    }
}
