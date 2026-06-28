package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.AgentRuntimeActionDraft;
import com.example.rag.model.dto.AgentRuntimeResponse;
import com.example.rag.model.dto.AgentRuntimeStepResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunMode;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 旧 JSON Runtime 两个核心演示场景的落库测试。 */
@ExtendWith(MockitoExtension.class)
class AgentRunScenarioTest {
    @Mock
    private AgentRunRepository agentRunRepository;
    @Mock
    private AgentStepRepository agentStepRepository;
    @Mock
    private AgentActionRepository agentActionRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private AgentRunResultService resultService;

    @BeforeEach
    void setUp() {
        resultService = new AgentRunResultService(
                agentRunRepository,
                agentStepRepository,
                agentActionRepository,
                snowflakeIdGenerator
        );
    }

    @Test
    void reembedRequiredScenarioShouldPersistRebuildActionAndWaitForConfirmation() {
        stubPersistence();

        AgentRunEntity result = resultService.complete("AR-100", runtimeResponse(
                "知识库当前不可问答，主要原因是 embedding 配置变化后尚未完成重嵌入。",
                "embedding.rebuild.submit",
                "{\"kbCode\":\"day20-cn-kb\"}"
        ));

        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(result.getSummary()).contains("重嵌入");
    }

    @Test
    void failedIndexingTaskScenarioShouldPersistRetryActionAndWaitForConfirmation() {
        stubPersistence();

        AgentRunEntity result = resultService.complete("AR-100", runtimeResponse(
                "知识库存在失败的索引任务，需要人工确认后重试失败任务。",
                "document.indexing_task.retry",
                "{\"kbCode\":\"day20-cn-kb\",\"taskId\":1001,\"documentCode\":\"DOC-failed-demo\"}"
        ));

        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(result.getSummary()).contains("失败的索引任务");
    }

    private void stubPersistence() {
        AgentRunEntity run = new AgentRunEntity();
        run.setId(100L);
        run.setRunCode("AR-100");
        run.setKnowledgeBaseId(1L);
        run.setGoal("诊断");
        run.setRunMode(AgentRunMode.DIAGNOSE_AND_RECOMMEND);
        run.setStatus(AgentRunStatus.RUNNING);
        run.setCreatedBy("tester");
        when(agentRunRepository.findByRunCode("AR-100")).thenReturn(Optional.of(run));
        when(agentRunRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentStepRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentActionRepository.insert(any())).thenAnswer(invocation -> {
            AgentActionEntity action = invocation.getArgument(0);
            assertThat(action.getStatus()).isEqualTo(AgentActionStatus.PENDING_CONFIRMATION);
            return action;
        });
        when(snowflakeIdGenerator.nextId()).thenReturn(201L, 301L);
        when(snowflakeIdGenerator.nextId("AST-")).thenReturn("AST-201");
        when(snowflakeIdGenerator.nextId("ACT-")).thenReturn("ACT-301");
    }

    private AgentRuntimeResponse runtimeResponse(String summary,
                                                 String toolName,
                                                 String actionPayload) {
        return new AgentRuntimeResponse(
                "SUCCEEDED",
                summary,
                List.of(new AgentRuntimeStepResult(
                        "diagnose",
                        null,
                        AgentStepType.NODE,
                        AgentStepStatus.SUCCEEDED,
                        null,
                        "{}",
                        1L,
                        null
                )),
                List.of(new AgentRuntimeActionDraft(
                        toolName,
                        "待确认动作",
                        "需要人工确认后执行",
                        AgentActionRiskLevel.MEDIUM,
                        true,
                        actionPayload
                )),
                null
        );
    }
}
