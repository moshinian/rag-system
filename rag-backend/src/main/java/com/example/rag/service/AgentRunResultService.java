package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.AgentRuntimeActionDraft;
import com.example.rag.model.dto.AgentRuntimeResponse;
import com.example.rag.model.dto.AgentRuntimeStepResult;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.AgentStepEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 将旧 JSON Runtime 响应事务化落入 run、step 和 action 表。
 */
@Service
public class AgentRunResultService {
    private static final String STEP_CODE_PREFIX = "AST-";
    private static final String ACTION_CODE_PREFIX = "ACT-";

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentActionRepository agentActionRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 构造 AgentRunResultService。 */
    public AgentRunResultService(AgentRunRepository agentRunRepository,
                                 AgentStepRepository agentStepRepository,
                                 AgentActionRepository agentActionRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator) {
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.agentActionRepository = agentActionRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 持久化旧 Runtime 完整响应并计算 Java 最终状态。 */
    @Transactional
    public AgentRunEntity complete(String runCode, AgentRuntimeResponse runtimeResponse) {
        AgentRunEntity run = requireRun(runCode);
        List<AgentRuntimeStepResult> runtimeSteps = runtimeResponse.steps() == null
                ? List.of()
                : runtimeResponse.steps();
        List<AgentRuntimeActionDraft> runtimeActions = runtimeResponse.recommendedActions() == null
                ? List.of()
                : runtimeResponse.recommendedActions();

        runtimeSteps.forEach(step -> persistRuntimeStep(runCode, step));
        List<AgentActionEntity> actions = runtimeActions.stream()
                .map(action -> persistRuntimeAction(runCode, action))
                .toList();

        run.setSummary(runtimeResponse.summary());
        run.setErrorMessage(runtimeResponse.errorMessage());
        if ("FAILED".equals(runtimeResponse.status())) {
            run.setStatus(AgentRunStatus.FAILED);
            run.setFinishedAt(OffsetDateTime.now());
        } else if (actions.stream().anyMatch(action -> Boolean.TRUE.equals(action.getRequiresConfirmation()))) {
            run.setStatus(AgentRunStatus.WAITING_CONFIRMATION);
            run.setFinishedAt(null);
        } else {
            run.setStatus(AgentRunStatus.SUCCEEDED);
            run.setFinishedAt(OffsetDateTime.now());
        }
        return agentRunRepository.updateById(run);
    }

    /** Runtime 调用或后台提交失败时把 run 收口为 FAILED。 */
    @Transactional
    public AgentRunEntity fail(String runCode, String errorMessage) {
        AgentRunEntity run = requireRun(runCode);
        run.setStatus(AgentRunStatus.FAILED);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(OffsetDateTime.now());
        return agentRunRepository.updateById(run);
    }

    /** 按业务编码读取 run。 */
    private AgentRunEntity requireRun(String runCode) {
        return agentRunRepository.findByRunCode(runCode)
                .orElseThrow(() -> new BusinessException("Agent run not found: " + runCode));
    }

    /** 持久化旧 Runtime 返回的 step。 */
    private AgentStepEntity persistRuntimeStep(String runCode, AgentRuntimeStepResult runtimeStep) {
        OffsetDateTime now = OffsetDateTime.now();
        AgentStepEntity entity = new AgentStepEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setRunCode(runCode);
        entity.setStepCode(snowflakeIdGenerator.nextId(STEP_CODE_PREFIX));
        entity.setNodeName(runtimeStep.nodeName());
        entity.setToolName(runtimeStep.toolName());
        entity.setStepType(runtimeStep.stepType());
        entity.setStatus(runtimeStep.status());
        entity.setInputJson(runtimeStep.inputJson());
        entity.setOutputJson(runtimeStep.outputJson());
        entity.setDurationMs(runtimeStep.durationMs());
        entity.setErrorMessage(runtimeStep.errorMessage());
        entity.setStartedAt(now);
        entity.setFinishedAt(now);
        return agentStepRepository.insert(entity);
    }

    /** 持久化旧 Runtime 返回的 action 草案。 */
    private AgentActionEntity persistRuntimeAction(String runCode, AgentRuntimeActionDraft draft) {
        AgentActionEntity entity = new AgentActionEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setRunCode(runCode);
        entity.setActionCode(snowflakeIdGenerator.nextId(ACTION_CODE_PREFIX));
        entity.setToolName(draft.toolName());
        entity.setTitle(draft.title());
        entity.setReason(draft.reason());
        entity.setRiskLevel(draft.riskLevel());
        entity.setRequiresConfirmation(draft.requiresConfirmation());
        entity.setStatus(AgentActionStatus.PENDING_CONFIRMATION);
        entity.setActionPayload(draft.actionPayload());
        return agentActionRepository.insert(entity);
    }
}
