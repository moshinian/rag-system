package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.integration.agent.AgentRuntimeClient;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.request.AgentActionConfirmRequest;
import com.example.rag.model.request.AgentActionRejectRequest;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.model.response.DocumentIndexingTaskResponse;
import com.example.rag.model.response.EmbeddingRebuildSubmitResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.service.agent.RecommendedActionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent 推荐动作确认执行测试。 */
@ExtendWith(MockitoExtension.class)
class AgentActionExecutionTest {
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private AgentStepRepository agentStepRepository;

    @Mock
    private AgentActionRepository agentActionRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private AgentRuntimeClient agentRuntimeClient;

    @Mock
    private DocumentIndexingService documentIndexingService;

    @Mock
    private EmbeddingRebuildService embeddingRebuildService;

    private AgentRunService agentRunService;

    @BeforeEach
    void setUp() {
        agentRunService = new AgentRunService(
                knowledgeBaseRepository,
                agentRunRepository,
                agentStepRepository,
                agentActionRepository,
                null,
                null,
                null,
                null,
                documentIndexingService,
                embeddingRebuildService,
                new RecommendedActionCatalog(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void confirmRetryActionShouldExecuteDocumentIndexingRetryAndMarkRunSucceeded() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = retryAction(AgentActionRiskLevel.MEDIUM);
        stubRunAndAction(knowledgeBase, run, action);
        when(documentIndexingService.retry("day20-cn-kb", "DOC-failed-demo", 1001L, "tester"))
                .thenReturn(indexingTaskResponse());

        AgentRunResponse response = agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(response.finishedAt()).isNotNull();
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.SUCCEEDED);
        assertThat(response.actions().get(0).confirmedBy()).isEqualTo("tester");
        assertThat(response.actions().get(0).confirmedAt()).isNotNull();
        assertThat(response.actions().get(0).executedAt()).isNotNull();
        assertThat(response.actions().get(0).resultJson()).contains("\"documentCode\":\"DOC-failed-demo\"");
        assertThat(response.actions().get(0).resultJson()).contains("\"taskId\":\"2001\"");
        verify(documentIndexingService).retry("day20-cn-kb", "DOC-failed-demo", 1001L, "tester");
    }

    @Test
    void confirmRetryActionShouldMarkActionAndRunFailedWhenRetryFails() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = retryAction(AgentActionRiskLevel.MEDIUM);
        stubRunAndAction(knowledgeBase, run, action);
        when(documentIndexingService.retry("day20-cn-kb", "DOC-failed-demo", 1001L, "tester"))
                .thenThrow(new BusinessException("Only FAILED indexing tasks can be retried: 1001"));

        AgentRunResponse response = agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(response.errorMessage()).contains("Only FAILED indexing tasks");
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.FAILED);
        assertThat(response.actions().get(0).errorMessage()).contains("Only FAILED indexing tasks");
        assertThat(response.actions().get(0).resultJson()).isNull();
    }

    @Test
    void confirmEmbeddingRebuildActionShouldSubmitRebuildAndMarkRunSucceeded() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = rebuildAction(AgentActionRiskLevel.MEDIUM);
        stubRunAndAction(knowledgeBase, run, action);
        when(embeddingRebuildService.submit("tester")).thenReturn(rebuildSubmitResponse());

        AgentRunResponse response = agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.SUCCEEDED);
        assertThat(response.actions().get(0).confirmedBy()).isEqualTo("tester");
        assertThat(response.actions().get(0).resultJson()).contains("\"rebuildRunId\":\"9001\"");
        assertThat(response.actions().get(0).resultJson()).contains("\"targetFingerprint\":\"fp-new\"");
        verify(embeddingRebuildService).submit("tester");
        verify(documentIndexingService, never()).retry(any(), any(), any(), any());
    }

    @Test
    void confirmEmbeddingRebuildActionShouldMarkFailedWhenSubmitFails() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = rebuildAction(AgentActionRiskLevel.MEDIUM);
        stubRunAndAction(knowledgeBase, run, action);
        when(embeddingRebuildService.submit("tester"))
                .thenThrow(new BusinessException("Embedding configuration has not changed"));

        AgentRunResponse response = agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(response.errorMessage()).contains("Embedding configuration has not changed");
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.FAILED);
        assertThat(response.actions().get(0).errorMessage()).contains("Embedding configuration has not changed");
        assertThat(response.actions().get(0).resultJson()).isNull();
    }

    @Test
    void confirmEmbeddingRebuildActionShouldRejectMismatchedPayloadKbCode() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = rebuildAction(AgentActionRiskLevel.MEDIUM);
        action.setActionPayload("{\"kbCode\":\"other-kb\"}");
        stubRunAndAction(knowledgeBase, run, action);

        AgentRunResponse response = agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.FAILED);
        assertThat(response.actions().get(0).errorMessage()).contains("kbCode does not match");
        verify(embeddingRebuildService, never()).submit(any());
    }

    @Test
    void confirmShouldRejectUnsupportedToolName() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = retryAction(AgentActionRiskLevel.MEDIUM);
        action.setToolName("unknown.write.tool");
        stubRunAndActionLookup(knowledgeBase, run, action);

        assertThatThrownBy(() -> agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not executable");

        verify(documentIndexingService, never()).retry(any(), any(), any(), any());
        verify(embeddingRebuildService, never()).submit(any());
    }

    @Test
    void confirmShouldRejectHighRiskAction() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = retryAction(AgentActionRiskLevel.HIGH);
        stubRunAndActionLookup(knowledgeBase, run, action);

        assertThatThrownBy(() -> agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HIGH risk");

        verify(documentIndexingService, never()).retry(any(), any(), any(), any());
    }

    @Test
    void rejectPendingActionShouldMarkRejectedAndFinishRun() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = retryAction(AgentActionRiskLevel.MEDIUM);
        stubRunAndAction(knowledgeBase, run, action);

        AgentRunResponse response = agentRunService.rejectAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionRejectRequest("reviewer", "暂不重试")
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(response.finishedAt()).isNotNull();
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.REJECTED);
        assertThat(response.actions().get(0).confirmedBy()).isEqualTo("reviewer");
        assertThat(response.actions().get(0).errorMessage()).isEqualTo("暂不重试");
        verify(documentIndexingService, never()).retry(any(), any(), any(), any());
    }

    @Test
    void confirmShouldRejectActionFromAnotherRun() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, AgentRunStatus.WAITING_CONFIRMATION);
        AgentActionEntity action = retryAction(AgentActionRiskLevel.MEDIUM);
        action.setRunCode("AR-other");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(agentRunRepository.findByRunCode("AR-100")).thenReturn(Optional.of(run));
        when(agentActionRepository.findByActionCode("ACT-100")).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> agentRunService.confirmAction(
                "day20-cn-kb",
                "AR-100",
                "ACT-100",
                new AgentActionConfirmRequest("tester")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to run");

        verify(documentIndexingService, never()).retry(any(), any(), any(), any());
    }

    private void stubRunAndAction(KnowledgeBaseEntity knowledgeBase, AgentRunEntity run, AgentActionEntity action) {
        stubRunAndActionLookup(knowledgeBase, run, action);
        org.mockito.Mockito.lenient().when(agentActionRepository.claimForExecution(
                any(), any(), any(), any())).thenReturn(true);
        when(agentActionRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentStepRepository.findByRunCode("AR-100")).thenReturn(List.of());
        when(agentActionRepository.findByRunCode("AR-100")).thenReturn(List.of(action));
    }

    private void stubRunAndActionLookup(KnowledgeBaseEntity knowledgeBase, AgentRunEntity run, AgentActionEntity action) {
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(agentRunRepository.findByRunCode("AR-100")).thenReturn(Optional.of(run));
        when(agentActionRepository.findByActionCode("ACT-100")).thenReturn(Optional.of(action));
    }

    private KnowledgeBaseEntity knowledgeBase(Long id, String kbCode) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setKbCode(kbCode);
        return entity;
    }

    private AgentRunEntity run(Long knowledgeBaseId, AgentRunStatus status) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(100L);
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setRunCode("AR-100");
        entity.setGoal("检查这个知识库有没有索引异常");
        entity.setStatus(status);
        entity.setCreatedBy("tester");
        return entity;
    }

    private AgentActionEntity retryAction(AgentActionRiskLevel riskLevel) {
        AgentActionEntity entity = new AgentActionEntity();
        entity.setId(200L);
        entity.setRunCode("AR-100");
        entity.setActionCode("ACT-100");
        entity.setToolName("document.indexing_task.retry");
        entity.setTitle("重试失败索引任务");
        entity.setReason("发现 FAILED indexing task");
        entity.setRiskLevel(riskLevel);
        entity.setRequiresConfirmation(true);
        entity.setStatus(AgentActionStatus.PENDING_CONFIRMATION);
        entity.setActionPayload("{\"kbCode\":\"day20-cn-kb\",\"taskId\":1001,\"documentCode\":\"DOC-failed-demo\"}");
        return entity;
    }

    private AgentActionEntity rebuildAction(AgentActionRiskLevel riskLevel) {
        AgentActionEntity entity = new AgentActionEntity();
        entity.setId(200L);
        entity.setRunCode("AR-100");
        entity.setActionCode("ACT-100");
        entity.setToolName("embedding.rebuild.submit");
        entity.setTitle("提交知识库重嵌入任务");
        entity.setReason("readiness 显示需要重嵌入");
        entity.setRiskLevel(riskLevel);
        entity.setRequiresConfirmation(true);
        entity.setStatus(AgentActionStatus.PENDING_CONFIRMATION);
        entity.setActionPayload("{\"kbCode\":\"day20-cn-kb\"}");
        return entity;
    }

    private DocumentIndexingTaskResponse indexingTaskResponse() {
        return new DocumentIndexingTaskResponse(
                2001L,
                "DOCUMENT_INDEXING",
                "QUEUED",
                "QUEUED",
                "MANUAL_RETRY",
                3001L,
                "DOC-failed-demo",
                "day20-cn-kb",
                1001L,
                "default",
                null,
                null,
                1,
                3,
                null,
                "tester",
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                OffsetDateTime.parse("2026-06-16T10:00:00Z")
        );
    }

    private EmbeddingRebuildSubmitResponse rebuildSubmitResponse() {
        return new EmbeddingRebuildSubmitResponse(
                9001L,
                "QUEUED",
                "fp-new",
                "text-embedding-v4",
                "aliyun-bailian-openai-compatible",
                1024,
                "cosine",
                "tester",
                OffsetDateTime.parse("2026-06-16T10:00:00Z")
        );
    }
}
