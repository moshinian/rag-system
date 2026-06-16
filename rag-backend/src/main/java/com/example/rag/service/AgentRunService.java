package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.AgentRunMode;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.model.response.AgentActionResponse;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.model.response.AgentStepResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.AgentStepEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent 运行管理服务。
 */
@Service
public class AgentRunService {
    private static final String RUN_CODE_PREFIX = "AR-";
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentActionRepository agentActionRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 构造AgentRunService。 */
    public AgentRunService(KnowledgeBaseRepository knowledgeBaseRepository,
                           AgentRunRepository agentRunRepository,
                           AgentStepRepository agentStepRepository,
                           AgentActionRepository agentActionRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.agentActionRepository = agentActionRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 创建一条 Agent 诊断运行记录。 */
    @Transactional
    public AgentRunResponse createRun(String kbCode, AgentRunCreateRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbCode);
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setRunCode(snowflakeIdGenerator.nextId(RUN_CODE_PREFIX));
        entity.setKnowledgeBaseId(knowledgeBase.getId());
        entity.setGoal(request.goal().trim());
        entity.setQuestion(trimToNull(request.question()));
        entity.setRunMode(request.runMode() == null ? AgentRunMode.DIAGNOSE_AND_RECOMMEND : request.runMode());
        entity.setStatus(AgentRunStatus.RUNNING);
        entity.setCreatedBy(defaultCreatedBy(request.createdBy()));
        AgentRunEntity saved = agentRunRepository.insert(entity);
        return toResponse(knowledgeBase, saved, List.of(), List.of());
    }

    /** 查询 Agent 诊断运行详情。 */
    @Transactional(readOnly = true)
    public AgentRunResponse getRun(String kbCode, String runCode) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbCode);
        AgentRunEntity run = agentRunRepository.findByRunCode(runCode)
                .orElseThrow(() -> new BusinessException("Agent run not found: " + runCode));
        if (!knowledgeBase.getId().equals(run.getKnowledgeBaseId())) {
            throw new BusinessException("Agent run does not belong to knowledge base: " + runCode);
        }
        List<AgentStepEntity> steps = agentStepRepository.findByRunCode(runCode);
        List<AgentActionEntity> actions = agentActionRepository.findByRunCode(runCode);
        return toResponse(knowledgeBase, run, steps, actions);
    }

    /** 查找知识库，不存在时抛出业务异常。 */
    private KnowledgeBaseEntity requireKnowledgeBase(String kbCode) {
        return knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
    }

    /** 组装 Agent 运行详情响应。 */
    private AgentRunResponse toResponse(KnowledgeBaseEntity knowledgeBase,
                                        AgentRunEntity run,
                                        List<AgentStepEntity> steps,
                                        List<AgentActionEntity> actions) {
        return new AgentRunResponse(
                run.getRunCode(),
                knowledgeBase.getKbCode(),
                run.getGoal(),
                run.getQuestion(),
                run.getRunMode(),
                run.getStatus(),
                run.getSummary(),
                run.getErrorMessage(),
                steps.stream().map(this::toStepResponse).toList(),
                actions.stream().map(this::toActionResponse).toList(),
                run.getCreatedBy(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                run.getFinishedAt()
        );
    }

    /** 组装 Agent 执行步骤响应。 */
    private AgentStepResponse toStepResponse(AgentStepEntity step) {
        return new AgentStepResponse(
                step.getStepCode(),
                step.getNodeName(),
                step.getToolName(),
                step.getStepType(),
                step.getStatus(),
                step.getInputJson(),
                step.getOutputJson(),
                step.getDurationMs(),
                step.getErrorMessage(),
                step.getStartedAt(),
                step.getFinishedAt(),
                step.getCreatedAt(),
                step.getUpdatedAt()
        );
    }

    /** 组装 Agent 推荐动作响应。 */
    private AgentActionResponse toActionResponse(AgentActionEntity action) {
        return new AgentActionResponse(
                action.getActionCode(),
                action.getToolName(),
                action.getTitle(),
                action.getReason(),
                action.getRiskLevel(),
                Boolean.TRUE.equals(action.getRequiresConfirmation()),
                action.getStatus(),
                action.getActionPayload(),
                action.getConfirmedBy(),
                action.getConfirmedAt(),
                action.getExecutedAt(),
                action.getResultJson(),
                action.getErrorMessage(),
                action.getCreatedAt(),
                action.getUpdatedAt()
        );
    }

    /** 把空白字符串归一化成 null。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 没有传入创建人时，统一记为 system。 */
    private String defaultCreatedBy(String createdBy) {
        String normalized = trimToNull(createdBy);
        return normalized == null ? "system" : normalized;
    }
}
