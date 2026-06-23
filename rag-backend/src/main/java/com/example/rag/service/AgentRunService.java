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
import com.example.rag.model.request.AgentActionConfirmRequest;
import com.example.rag.model.request.AgentActionRejectRequest;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.model.response.AgentActionResponse;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.model.response.AgentStepResponse;
import com.example.rag.model.response.DocumentIndexingTaskResponse;
import com.example.rag.model.response.EmbeddingRebuildSubmitResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.AgentStepEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.service.agent.RecommendedActionCatalog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Agent 运行管理服务。
 */
@Service
public class AgentRunService {
    private static final String RUN_CODE_PREFIX = "AR-";
    private static final String STEP_CODE_PREFIX = "AST-";
    private static final String ACTION_CODE_PREFIX = "ACT-";
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentActionRepository agentActionRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AgentRuntimeClient agentRuntimeClient;
    private final DocumentIndexingService documentIndexingService;
    private final EmbeddingRebuildService embeddingRebuildService;
    private final RecommendedActionCatalog recommendedActionCatalog;
    private final ObjectMapper objectMapper;

    /** 构造AgentRunService。 */
    public AgentRunService(KnowledgeBaseRepository knowledgeBaseRepository,
                           AgentRunRepository agentRunRepository,
                           AgentStepRepository agentStepRepository,
                           AgentActionRepository agentActionRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           AgentRuntimeClient agentRuntimeClient,
                           DocumentIndexingService documentIndexingService,
                           EmbeddingRebuildService embeddingRebuildService,
                           RecommendedActionCatalog recommendedActionCatalog,
                           ObjectMapper objectMapper) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.agentActionRepository = agentActionRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.agentRuntimeClient = agentRuntimeClient;
        this.documentIndexingService = documentIndexingService;
        this.embeddingRebuildService = embeddingRebuildService;
        this.recommendedActionCatalog = recommendedActionCatalog;
        this.objectMapper = objectMapper;
    }

    /** 创建一条 Agent 诊断运行记录。 */
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
        try {
            AgentRuntimeResponse runtimeResponse = agentRuntimeClient.run(buildRuntimeRequest(kbCode, saved));
            return completeRun(knowledgeBase, saved, runtimeResponse);
        } catch (BusinessException ex) {
            return failRun(knowledgeBase, saved, ex.getMessage());
        }
    }

    /** 查询 Agent 诊断运行详情。 */
    @Transactional(readOnly = true)
    public AgentRunResponse getRun(String kbCode, String runCode) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbCode);
        AgentRunEntity run = requireRunForKnowledgeBase(knowledgeBase, runCode);
        List<AgentStepEntity> steps = agentStepRepository.findByRunCode(runCode);
        List<AgentActionEntity> actions = agentActionRepository.findByRunCode(runCode);
        return toResponse(knowledgeBase, run, steps, actions);
    }

    /** 确认并执行一条待人工确认的 Agent 推荐动作。 */
    public AgentRunResponse confirmAction(String kbCode,
                                          String runCode,
                                          String actionCode,
                                          AgentActionConfirmRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbCode);
        AgentRunEntity run = requireRunForKnowledgeBase(knowledgeBase, runCode);
        AgentActionEntity action = requireActionForRun(runCode, actionCode);
        ensureConfirmable(run, action);
        ensureExecutableAction(action);

        OffsetDateTime now = OffsetDateTime.now();
        String operator = defaultCreatedBy(request == null ? null : request.operator());
        action.setStatus(AgentActionStatus.EXECUTING);
        action.setConfirmedBy(operator);
        action.setConfirmedAt(now);
        action.setErrorMessage(null);
        agentActionRepository.updateById(action);

        try {
            Object result = executeConfirmedAction(kbCode, action, operator);
            action.setStatus(AgentActionStatus.SUCCEEDED);
            action.setExecutedAt(OffsetDateTime.now());
            action.setResultJson(toJson(result));
            action.setErrorMessage(null);
            agentActionRepository.updateById(action);

            run.setStatus(AgentRunStatus.SUCCEEDED);
            run.setErrorMessage(null);
            run.setFinishedAt(OffsetDateTime.now());
            AgentRunEntity updatedRun = agentRunRepository.updateById(run);
            return currentResponse(knowledgeBase, updatedRun);
        } catch (BusinessException ex) {
            action.setStatus(AgentActionStatus.FAILED);
            action.setExecutedAt(OffsetDateTime.now());
            action.setResultJson(null);
            action.setErrorMessage(ex.getMessage());
            agentActionRepository.updateById(action);

            run.setStatus(AgentRunStatus.FAILED);
            run.setErrorMessage(ex.getMessage());
            run.setFinishedAt(OffsetDateTime.now());
            AgentRunEntity updatedRun = agentRunRepository.updateById(run);
            return currentResponse(knowledgeBase, updatedRun);
        }
    }

    /** 拒绝一条待人工确认的 Agent 推荐动作。 */
    public AgentRunResponse rejectAction(String kbCode,
                                         String runCode,
                                         String actionCode,
                                         AgentActionRejectRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbCode);
        AgentRunEntity run = requireRunForKnowledgeBase(knowledgeBase, runCode);
        AgentActionEntity action = requireActionForRun(runCode, actionCode);
        if (action.getStatus() != AgentActionStatus.PENDING_CONFIRMATION) {
            throw new BusinessException("Agent action is not pending confirmation: " + actionCode);
        }

        action.setStatus(AgentActionStatus.REJECTED);
        action.setConfirmedBy(defaultCreatedBy(request == null ? null : request.operator()));
        action.setConfirmedAt(OffsetDateTime.now());
        action.setErrorMessage(trimToNull(request == null ? null : request.reason()));
        agentActionRepository.updateById(action);

        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinishedAt(OffsetDateTime.now());
        AgentRunEntity updatedRun = agentRunRepository.updateById(run);
        return currentResponse(knowledgeBase, updatedRun);
    }

    /** 查找知识库，不存在时抛出业务异常。 */
    private KnowledgeBaseEntity requireKnowledgeBase(String kbCode) {
        return knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
    }

    /** 查询并校验 run 属于指定知识库。 */
    private AgentRunEntity requireRunForKnowledgeBase(KnowledgeBaseEntity knowledgeBase, String runCode) {
        AgentRunEntity run = agentRunRepository.findByRunCode(runCode)
                .orElseThrow(() -> new BusinessException("Agent run not found: " + runCode));
        if (!knowledgeBase.getId().equals(run.getKnowledgeBaseId())) {
            throw new BusinessException("Agent run does not belong to knowledge base: " + runCode);
        }
        return run;
    }

    /** 查询并校验 action 属于指定 run。 */
    private AgentActionEntity requireActionForRun(String runCode, String actionCode) {
        AgentActionEntity action = agentActionRepository.findByActionCode(actionCode)
                .orElseThrow(() -> new BusinessException("Agent action not found: " + actionCode));
        if (!runCode.equals(action.getRunCode())) {
            throw new BusinessException("Agent action does not belong to run: " + actionCode);
        }
        return action;
    }

    /** 校验 run/action 当前允许人工确认。 */
    private void ensureConfirmable(AgentRunEntity run, AgentActionEntity action) {
        if (run.getStatus() != AgentRunStatus.WAITING_CONFIRMATION) {
            throw new BusinessException("Agent run is not waiting for confirmation: " + run.getRunCode());
        }
        if (action.getStatus() != AgentActionStatus.PENDING_CONFIRMATION) {
            throw new BusinessException("Agent action is not pending confirmation: " + action.getActionCode());
        }
        if (!Boolean.TRUE.equals(action.getRequiresConfirmation())) {
            throw new BusinessException("Agent action does not require confirmation: " + action.getActionCode());
        }
    }

    /** 校验确认执行动作在当前白名单和风险边界内。 */
    private void ensureExecutableAction(AgentActionEntity action) {
        if (action.getRiskLevel() == AgentActionRiskLevel.HIGH) {
            throw new BusinessException("HIGH risk agent action cannot be executed: " + action.getActionCode());
        }
        if (!recommendedActionCatalog.isExecutable(action.getToolName(), action.getRiskLevel())) {
            throw new BusinessException("Agent action tool is not executable: " + action.getToolName());
        }
    }

    /** 执行已确认的 Agent 推荐动作。 */
    private Object executeConfirmedAction(String kbCode, AgentActionEntity action, String operator) {
        return switch (action.getToolName()) {
            case RecommendedActionCatalog.RETRY_INDEXING_TASK_TOOL -> executeRetryIndexingTask(kbCode, action, operator);
            case RecommendedActionCatalog.SUBMIT_EMBEDDING_REBUILD_TOOL -> executeEmbeddingRebuildSubmit(kbCode, action, operator);
            default -> throw new BusinessException("Agent action tool is not executable: " + action.getToolName());
        };
    }

    /** 执行失败索引任务重试。 */
    private DocumentIndexingTaskResponse executeRetryIndexingTask(String kbCode, AgentActionEntity action, String operator) {
        DocumentIndexingTaskRetryPayload payload = parseRetryPayload(kbCode, action);
        return documentIndexingService.retry(
                kbCode,
                payload.documentCode(),
                payload.taskId(),
                operator
        );
    }

    /** 执行全量重嵌入提交。 */
    private EmbeddingRebuildSubmitResponse executeEmbeddingRebuildSubmit(String kbCode, AgentActionEntity action, String operator) {
        ensurePayloadKbCodeMatchesPath(kbCode, action);
        return embeddingRebuildService.submit(operator);
    }

    /** 解析 document.indexing_task.retry action payload。 */
    private DocumentIndexingTaskRetryPayload parseRetryPayload(String pathKbCode, AgentActionEntity action) {
        if (trimToNull(action.getActionPayload()) == null) {
            throw new BusinessException("Agent action payload must not be blank");
        }
        try {
            JsonNode root = objectMapper.readTree(action.getActionPayload());
            String payloadKbCode = textField(root, "kbCode");
            if (payloadKbCode != null && !pathKbCode.equals(payloadKbCode)) {
                throw new BusinessException("Agent action payload kbCode does not match path kbCode");
            }
            String documentCode = textField(root, "documentCode");
            if (documentCode == null) {
                throw new BusinessException("Agent action payload documentCode must not be blank");
            }
            JsonNode taskIdNode = root.get("taskId");
            if (taskIdNode == null || !taskIdNode.canConvertToLong()) {
                throw new BusinessException("Agent action payload taskId must be a valid long");
            }
            return new DocumentIndexingTaskRetryPayload(documentCode, taskIdNode.longValue());
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to parse agent action payload: " + ex.getMessage());
        }
    }

    /** 校验 action payload 中的 kbCode 不和 path kbCode 冲突。 */
    private void ensurePayloadKbCodeMatchesPath(String pathKbCode, AgentActionEntity action) {
        if (trimToNull(action.getActionPayload()) == null) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(action.getActionPayload());
            String payloadKbCode = textField(root, "kbCode");
            if (payloadKbCode != null && !pathKbCode.equals(payloadKbCode)) {
                throw new BusinessException("Agent action payload kbCode does not match path kbCode");
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to parse agent action payload: " + ex.getMessage());
        }
    }

    /** 把字段读取为去空白后的字符串。 */
    private String textField(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return trimToNull(node.asText());
    }

    /** 序列化 action 执行结果。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize agent action result: " + ex.getMessage());
        }
    }

    /** 查询当前 run 的完整响应。 */
    private AgentRunResponse currentResponse(KnowledgeBaseEntity knowledgeBase, AgentRunEntity run) {
        List<AgentStepEntity> steps = agentStepRepository.findByRunCode(run.getRunCode());
        List<AgentActionEntity> actions = agentActionRepository.findByRunCode(run.getRunCode());
        return toResponse(knowledgeBase, run, steps, actions);
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

    /** 构造 Python Agent Runtime 请求。 */
    private AgentRuntimeRequest buildRuntimeRequest(String kbCode, AgentRunEntity run) {
        return new AgentRuntimeRequest(
                run.getRunCode(),
                kbCode,
                run.getGoal(),
                run.getQuestion(),
                run.getRunMode()
        );
    }

    /** 使用 Python Runtime 结果完成 run。 */
    private AgentRunResponse completeRun(KnowledgeBaseEntity knowledgeBase,
                                         AgentRunEntity run,
                                         AgentRuntimeResponse runtimeResponse) {
        List<AgentRuntimeStepResult> runtimeSteps = runtimeResponse.steps() == null
                ? List.of()
                : runtimeResponse.steps();
        List<AgentRuntimeActionDraft> runtimeActions = runtimeResponse.recommendedActions() == null
                ? List.of()
                : runtimeResponse.recommendedActions();

        List<AgentStepEntity> steps = runtimeSteps.stream()
                .map(step -> persistRuntimeStep(run.getRunCode(), step))
                .toList();
        List<AgentActionEntity> actions = runtimeActions.stream()
                .map(action -> persistRuntimeAction(run.getRunCode(), action))
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
        AgentRunEntity updated = agentRunRepository.updateById(run);
        return toResponse(knowledgeBase, updated, steps, actions);
    }

    /** Runtime 调用失败时把 run 收口到 FAILED。 */
    private AgentRunResponse failRun(KnowledgeBaseEntity knowledgeBase, AgentRunEntity run, String errorMessage) {
        run.setStatus(AgentRunStatus.FAILED);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(OffsetDateTime.now());
        AgentRunEntity updated = agentRunRepository.updateById(run);
        return toResponse(knowledgeBase, updated, List.of(), List.of());
    }

    /** 持久化 Python Runtime 返回的 step。 */
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

    /** 持久化 Python Runtime 返回的 action 草案。 */
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

    /** document.indexing_task.retry action payload。 */
    private record DocumentIndexingTaskRetryPayload(String documentCode, Long taskId) {
    }
}
