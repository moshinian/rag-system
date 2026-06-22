package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.integration.agent.AgentRuntimeClient;
import com.example.rag.model.dto.AgentRuntimeActionDraft;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.example.rag.model.dto.AgentRuntimeResponse;
import com.example.rag.model.dto.AgentRuntimeStepResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunMode;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent 运行管理服务测试。 */
@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {

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

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AgentRunService agentRunService;

    @Test
    void createRunShouldCallRuntimePersistTraceAndWaitForConfirmation() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 200L, 300L);
        when(snowflakeIdGenerator.nextId("AR-")).thenReturn("AR-100");
        when(snowflakeIdGenerator.nextId("AST-")).thenReturn("AST-200");
        when(snowflakeIdGenerator.nextId("ACT-")).thenReturn("ACT-300");
        when(agentRunRepository.insert(any())).thenAnswer(invocation -> {
            AgentRunEntity entity = invocation.getArgument(0);
            entity.setCreatedAt(OffsetDateTime.parse("2026-06-16T10:00:00Z"));
            entity.setUpdatedAt(OffsetDateTime.parse("2026-06-16T10:00:00Z"));
            return entity;
        });
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentStepRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentActionRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRuntimeClient.run(any())).thenReturn(new AgentRuntimeResponse(
                "SUCCEEDED",
                "知识库当前不可问答，主要原因是 embedding 配置变化后尚未完成重嵌入。",
                List.of(new AgentRuntimeStepResult(
                        "kb_readiness_check",
                        "kb.readiness.check",
                        AgentStepType.TOOL_CALL,
                        AgentStepStatus.SUCCEEDED,
                        null,
                        "{\"reembedRequired\":true}",
                        12L,
                        null
                )),
                List.of(new AgentRuntimeActionDraft(
                        "embedding.rebuild.submit",
                        "提交知识库重嵌入任务",
                        "readiness 显示需要重嵌入",
                        AgentActionRiskLevel.MEDIUM,
                        true,
                        "{\"kbCode\":\"day20-cn-kb\"}"
                )),
                null
        ));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest(
                        "  诊断这个知识库为什么不能问答  ",
                        "  检索探测问题  ",
                        null,
                        "  operator-a  "
                )
        );

        assertThat(response.runCode()).isEqualTo("AR-100");
        assertThat(response.knowledgeBaseCode()).isEqualTo("day20-cn-kb");
        assertThat(response.goal()).isEqualTo("诊断这个知识库为什么不能问答");
        assertThat(response.question()).isEqualTo("检索探测问题");
        assertThat(response.runMode()).isEqualTo(AgentRunMode.DIAGNOSE_AND_RECOMMEND);
        assertThat(response.status()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(response.summary()).contains("重嵌入");
        assertThat(response.createdBy()).isEqualTo("operator-a");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).stepCode()).isEqualTo("AST-200");
        assertThat(response.steps().get(0).nodeName()).isEqualTo("kb_readiness_check");
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).actionCode()).isEqualTo("ACT-300");
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.PENDING_CONFIRMATION);
        assertThat(response.finishedAt()).isNull();

        verify(agentRuntimeClient).run(argThat(runtimeRequest ->
                runtimeRequest.runCode().equals("AR-100")
                        && runtimeRequest.kbCode().equals("day20-cn-kb")
                        && runtimeRequest.runMode() == AgentRunMode.DIAGNOSE_AND_RECOMMEND
        ));
    }

    @Test
    void createRunShouldMarkSucceededWhenRuntimeReturnsNoAction() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(snowflakeIdGenerator.nextId("AR-")).thenReturn("AR-100");
        when(agentRunRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRuntimeClient.run(any())).thenReturn(new AgentRuntimeResponse(
                "SUCCEEDED",
                "当前未发现阻断问答的 readiness 问题。",
                List.of(),
                List.of(),
                null
        ));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest("诊断", null, AgentRunMode.DIAGNOSE_ONLY, null)
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(response.finishedAt()).isNotNull();
        assertThat(response.actions()).isEmpty();
    }

    @Test
    void createRunShouldPersistRetryActionDraftFromRuntime() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 300L);
        when(snowflakeIdGenerator.nextId("AR-")).thenReturn("AR-100");
        when(snowflakeIdGenerator.nextId("ACT-")).thenReturn("ACT-300");
        when(agentRunRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentActionRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRuntimeClient.run(any())).thenReturn(new AgentRuntimeResponse(
                "SUCCEEDED",
                "知识库存在失败的索引任务，需要人工确认后重试失败任务。",
                List.of(),
                List.of(new AgentRuntimeActionDraft(
                        "document.indexing_task.retry",
                        "重试失败索引任务",
                        "发现 FAILED indexing task",
                        AgentActionRiskLevel.MEDIUM,
                        true,
                        "{\"kbCode\":\"day20-cn-kb\",\"taskId\":1001,\"documentCode\":\"DOC-2\"}"
                )),
                null
        ));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest("检查这个知识库有没有索引异常", null, null, null)
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).actionCode()).isEqualTo("ACT-300");
        assertThat(response.actions().get(0).toolName()).isEqualTo("document.indexing_task.retry");
        assertThat(response.actions().get(0).status()).isEqualTo(AgentActionStatus.PENDING_CONFIRMATION);
        assertThat(response.actions().get(0).actionPayload()).contains("\"taskId\":1001");
    }

    @Test
    void createRunShouldMarkFailedWhenRuntimeReturnsFailed() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(snowflakeIdGenerator.nextId("AR-")).thenReturn("AR-100");
        when(agentRunRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRuntimeClient.run(any())).thenReturn(new AgentRuntimeResponse(
                "FAILED",
                "Agent 诊断失败。",
                List.of(),
                List.of(),
                "backend unavailable"
        ));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest("诊断", null, null, null)
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("backend unavailable");
        assertThat(response.finishedAt()).isNotNull();
    }

    @Test
    void createRunShouldPersistFailedRunWhenRuntimeClientThrows() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(snowflakeIdGenerator.nextId("AR-")).thenReturn("AR-100");
        when(agentRunRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRuntimeClient.run(any())).thenThrow(new BusinessException("Failed to call Agent Runtime: timeout"));

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest("诊断", null, null, null)
        );

        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(response.errorMessage()).contains("timeout");
        assertThat(response.finishedAt()).isNotNull();
    }

    @Test
    void getRunShouldReturnRunWithEmptyStepsAndActions() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(1L, "AR-100");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(agentRunRepository.findByRunCode("AR-100")).thenReturn(Optional.of(run));
        when(agentStepRepository.findByRunCode("AR-100")).thenReturn(List.of());
        when(agentActionRepository.findByRunCode("AR-100")).thenReturn(List.of());

        AgentRunResponse response = agentRunService.getRun("day20-cn-kb", "AR-100");

        assertThat(response.runCode()).isEqualTo("AR-100");
        assertThat(response.steps()).isEmpty();
        assertThat(response.actions()).isEmpty();
    }

    @Test
    void getRunShouldRejectRunFromAnotherKnowledgeBase() {
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        AgentRunEntity run = run(2L, "AR-100");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(agentRunRepository.findByRunCode("AR-100")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> agentRunService.getRun("day20-cn-kb", "AR-100"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");

        verify(agentStepRepository, never()).findByRunCode(any());
        verify(agentActionRepository, never()).findByRunCode(any());
    }

    @Test
    void createRunShouldRejectMissingKnowledgeBase() {
        when(knowledgeBaseRepository.findByCode("missing-kb")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentRunService.createRun(
                "missing-kb",
                new AgentRunCreateRequest("诊断", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Knowledge base not found");

        verify(agentRunRepository, never()).insert(any());
        verify(agentRuntimeClient, never()).run(any(AgentRuntimeRequest.class));
    }

    private KnowledgeBaseEntity knowledgeBase(Long id, String kbCode) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setKbCode(kbCode);
        return entity;
    }

    private AgentRunEntity run(Long knowledgeBaseId, String runCode) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(100L);
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setRunCode(runCode);
        entity.setGoal("诊断");
        entity.setRunMode(AgentRunMode.DIAGNOSE_AND_RECOMMEND);
        entity.setStatus(AgentRunStatus.RUNNING);
        entity.setCreatedBy("system");
        return entity;
    }
}
