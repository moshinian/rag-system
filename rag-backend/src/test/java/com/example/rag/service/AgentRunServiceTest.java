package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.service.agent.RecommendedActionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private AgentRunRecordService runRecordService;
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
                runRecordService,
                documentIndexingService,
                embeddingRebuildService,
                new RecommendedActionCatalog(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void createRunShouldReturnQueuedWithoutBindingToLocalExecutor() {
        AgentRunEntity run = run(1L, "AR-100");
        run.setStatus(AgentRunStatus.QUEUED);
        when(runRecordService.create(any(), any())).thenReturn(run);

        AgentRunResponse response = agentRunService.createRun(
                "day20-cn-kb",
                new AgentRunCreateRequest(
                        "诊断这个知识库为什么不能问答",
                        "第二百三十八条是什么",
                        "tester"
                )
        );

        assertThat(response.runCode()).isEqualTo("AR-100");
        assertThat(response.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(response.steps()).isEmpty();
        assertThat(response.actions()).isEmpty();
    }

    @Test
    void createRunShouldPropagateMissingKnowledgeBaseFromRecordService() {
        when(runRecordService.create(any(), any()))
                .thenThrow(new BusinessException("Knowledge base not found: missing-kb"));

        assertThatThrownBy(() -> agentRunService.createRun(
                "missing-kb",
                new AgentRunCreateRequest("诊断", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Knowledge base not found");

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
        entity.setStatus(AgentRunStatus.RUNNING);
        entity.setCreatedBy("system");
        return entity;
    }
}
