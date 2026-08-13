package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.dto.AgentRuntimeEvent;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentRuntimeEventType;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.AgentStepEntity;
import com.example.rag.service.agent.RecommendedActionCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Runtime event 事务应用规则测试。 */
@ExtendWith(MockitoExtension.class)
class AgentRunEventApplierTest {
    @Mock
    private AgentRunEventService eventService;
    @Mock
    private AgentRunRepository runRepository;
    @Mock
    private AgentStepRepository stepRepository;
    @Mock
    private AgentActionRepository actionRepository;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    private ObjectMapper objectMapper;
    private AgentRunEventApplier applier;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        applier = new AgentRunEventApplier(
                eventService,
                runRepository,
                stepRepository,
                actionRepository,
                new RecommendedActionCatalog(),
                idGenerator,
                objectMapper
        );
    }

    @Test
    void stepEventsShouldUseRunAndNodeInvocationId() throws Exception {
        AgentRunEntity run = run();
        AgentStepEntity persisted = new AgentStepEntity();
        persisted.setId(200L);
        persisted.setRunCode("AR-1");
        persisted.setNodeInvocationId("AR-1-N-1");
        persisted.setStepCode("AST-1");
        persisted.setNodeName("llm_plan");
        persisted.setStatus(AgentStepStatus.RUNNING);
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));
        when(eventService.persist(any())).thenReturn(Optional.of(response()));
        when(stepRepository.findByRunCodeAndNodeInvocationId("AR-1", "AR-1-N-1"))
                .thenReturn(Optional.<AgentStepEntity>empty())
                .thenReturn(Optional.of(persisted));
        when(idGenerator.nextId()).thenReturn(200L);
        when(idGenerator.nextId("AST-")).thenReturn("AST-1");

        applier.apply(event(
                "EVT-1",
                AgentRuntimeEventType.STEP_STARTED,
                "AR-1-N-1",
                "llm_plan",
                null,
                "RUNNING",
                objectMapper.createObjectNode(),
                false
        ));
        applier.apply(event(
                "EVT-2",
                AgentRuntimeEventType.STEP_COMPLETED,
                "AR-1-N-1",
                "llm_plan",
                null,
                "SUCCEEDED",
                objectMapper.readTree("""
                        {
                          "stepType": "LLM_DECISION",
                          "outputJson": "{\\"decision\\":{\\"action\\":\\"FINAL_ANSWER\\"}}",
                          "durationMs": 12
                        }
                        """),
                false
        ));

        ArgumentCaptor<AgentStepEntity> insertCaptor = ArgumentCaptor.forClass(AgentStepEntity.class);
        verify(stepRepository).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getNodeInvocationId()).isEqualTo("AR-1-N-1");
        verify(stepRepository).updateById(persisted);
        assertThat(persisted.getStepType().name()).isEqualTo("LLM_DECISION");
        assertThat(persisted.getStatus()).isEqualTo(AgentStepStatus.SUCCEEDED);
        assertThat(persisted.getDurationMs()).isEqualTo(12L);
    }

    @Test
    void duplicateActionEventShouldNotCreateAction() throws Exception {
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run()));
        when(eventService.persist(any())).thenReturn(Optional.empty());

        boolean inserted = applier.apply(actionEvent());

        assertThat(inserted).isFalse();
        verify(actionRepository, never()).insert(any());
    }

    @Test
    void recoveredAttemptShouldUseLeaseScopedEventAndNodeIdentifiers() {
        AgentRunEntity run = run();
        run.setLeaseVersion(2L);
        run.setOwnerInstanceId("pod-b");
        when(runRepository.lockOwned("AR-1", "pod-b", 2L)).thenReturn(Optional.of(run));
        when(eventService.persist(any())).thenReturn(Optional.empty());

        boolean inserted = applier.applyOwned(event(
                "AR-1-000001",
                AgentRuntimeEventType.STEP_STARTED,
                "AR-1-N-000001",
                "llm_plan",
                null,
                "RUNNING",
                objectMapper.createObjectNode(),
                false
        ), "pod-b", 2L);

        ArgumentCaptor<AgentRunEventDraft> captor = ArgumentCaptor.forClass(AgentRunEventDraft.class);
        verify(eventService).persist(captor.capture());
        assertThat(inserted).isFalse();
        assertThat(captor.getValue().eventCode()).isEqualTo("AR-1-000001-A2");
        assertThat(captor.getValue().nodeInvocationId()).isEqualTo("AR-1-N-000001-A2");
    }

    @Test
    void lateRuntimeEventAfterTerminalRunShouldBeIgnoredBeforePersistingEvent() {
        AgentRunEntity run = run();
        run.setStatus(AgentRunStatus.FAILED);
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));

        boolean inserted = applier.apply(event(
                "EVT-late",
                AgentRuntimeEventType.RUN_COMPLETED,
                null,
                null,
                null,
                "SUCCEEDED",
                objectMapper.createObjectNode(),
                true
        ));

        assertThat(inserted).isFalse();
        verify(eventService, never()).persist(any());
        verify(runRepository, never()).updateById(any());
    }

    @Test
    void actionRecommendedShouldUseJavaCatalogPolicy() throws Exception {
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run()));
        when(eventService.persist(any())).thenReturn(Optional.of(response()));
        when(idGenerator.nextId()).thenReturn(300L);
        when(idGenerator.nextId("ACT-")).thenReturn("ACT-1");

        applier.apply(actionEvent());

        ArgumentCaptor<AgentActionEntity> captor = ArgumentCaptor.forClass(AgentActionEntity.class);
        verify(actionRepository).insert(captor.capture());
        assertThat(captor.getValue().getRiskLevel()).isEqualTo(AgentActionRiskLevel.MEDIUM);
        assertThat(captor.getValue().getRequiresConfirmation()).isTrue();
        assertThat(captor.getValue().getActionCode()).isEqualTo("ACT-1");
    }

    @Test
    void runCompletedWithPendingActionShouldOnlyPersistWaitingTerminal() throws Exception {
        AgentRunEntity run = run();
        when(runRepository.findByRunCode("AR-1")).thenReturn(Optional.of(run));
        when(actionRepository.existsPendingConfirmation("AR-1")).thenReturn(true);
        when(eventService.persist(any())).thenReturn(Optional.of(response()));

        applier.apply(event(
                "EVT-terminal",
                AgentRuntimeEventType.RUN_COMPLETED,
                null,
                null,
                null,
                "SUCCEEDED",
                objectMapper.readTree("{\"summary\":\"等待确认\"}"),
                true
        ));

        ArgumentCaptor<AgentRunEventDraft> eventCaptor =
                ArgumentCaptor.forClass(AgentRunEventDraft.class);
        verify(eventService).persist(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(AgentRunEventType.RUN_WAITING_CONFIRMATION);
        assertThat(eventCaptor.getValue().payloadJson())
                .contains("\"pythonEventType\":\"RUN_COMPLETED\"");
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(run.getFinishedAt()).isNull();
        verify(runRepository).updateById(run);
    }

    private AgentRuntimeEvent actionEvent() throws Exception {
        return event(
                "EVT-action",
                AgentRuntimeEventType.ACTION_RECOMMENDED,
                "AR-1-N-2",
                "create_recommended_action",
                "embedding.rebuild.submit",
                "PENDING_CONFIRMATION",
                objectMapper.readTree("""
                        {
                          "title": "提交重嵌入",
                          "reason": "需要重嵌入",
                          "riskLevel": "HIGH",
                          "requiresConfirmation": false,
                          "actionPayload": "{\\"kbCode\\":\\"kb-1\\"}"
                        }
                        """),
                false
        );
    }

    private AgentRuntimeEvent event(String eventId,
                                    AgentRuntimeEventType type,
                                    String invocationId,
                                    String nodeName,
                                    String toolName,
                                    String status,
                                    JsonNode payload,
                                    boolean terminal) {
        return new AgentRuntimeEvent(
                eventId,
                "AR-1",
                type,
                invocationId,
                nodeName,
                toolName,
                status,
                "message",
                payload,
                terminal,
                OffsetDateTime.parse("2026-06-24T12:00:00Z")
        );
    }

    private AgentRunEntity run() {
        AgentRunEntity run = new AgentRunEntity();
        run.setId(100L);
        run.setRunCode("AR-1");
        run.setKnowledgeBaseId(1L);
        run.setGoal("诊断");
        run.setStatus(AgentRunStatus.RUNNING);
        run.setCreatedBy("tester");
        return run;
    }

    private AgentRunEventResponse response() {
        return new AgentRunEventResponse(
                1L,
                "EVT",
                "AR-1",
                AgentRunEventType.RUN_STARTED,
                null,
                null,
                null,
                null,
                null,
                "{}",
                false,
                OffsetDateTime.now()
        );
    }
}
