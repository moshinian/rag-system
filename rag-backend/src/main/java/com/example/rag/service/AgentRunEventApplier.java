package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.dto.AgentRuntimeEvent;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentRuntimeEventType;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.AgentStepEntity;
import com.example.rag.service.agent.RecommendedActionCatalog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 将 Python Runtime event 事务化应用到 Java 权威状态。
 */
@Service
public class AgentRunEventApplier {
    private static final Logger log = LoggerFactory.getLogger(AgentRunEventApplier.class);
    private static final String STEP_CODE_PREFIX = "AST-";
    private static final String ACTION_CODE_PREFIX = "ACT-";

    private final AgentRunEventService eventService;
    private final AgentRunRepository runRepository;
    private final AgentStepRepository stepRepository;
    private final AgentActionRepository actionRepository;
    private final RecommendedActionCatalog actionCatalog;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /** 构造 AgentRunEventApplier。 */
    public AgentRunEventApplier(AgentRunEventService eventService,
                                AgentRunRepository runRepository,
                                AgentStepRepository stepRepository,
                                AgentActionRepository actionRepository,
                                RecommendedActionCatalog actionCatalog,
                                SnowflakeIdGenerator idGenerator,
                                ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.actionRepository = actionRepository;
        this.actionCatalog = actionCatalog;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 幂等应用一条 Runtime event。
     *
     * <p>事件首次插入成功后才修改 step/action/run；整个过程与事件落库处于同一事务。</p>
     */
    @Transactional
    public boolean apply(AgentRuntimeEvent runtimeEvent) {
        validateRuntimeEvent(runtimeEvent);
        AgentRunEntity run = requireRun(runtimeEvent.runCode());
        if (isTerminalRunStatus(run.getStatus())) {
            log.info(StructuredLogMessage.of("agent.runtime.event.ignored_after_terminal")
                    .field("runCode", runtimeEvent.runCode())
                    .field("eventCode", runtimeEvent.eventId())
                    .field("runtimeEventType", runtimeEvent.type())
                    .field("currentRunStatus", run.getStatus())
                    .build());
            return false;
        }

        AgentRunEventType frontendType = normalizeEventType(runtimeEvent);
        AgentRunEventDraft draft = new AgentRunEventDraft(
                runtimeEvent.eventId(),
                runtimeEvent.runCode(),
                runtimeEvent.nodeInvocationId(),
                frontendType,
                runtimeEvent.nodeName(),
                runtimeEvent.toolName(),
                normalizeStatus(runtimeEvent, frontendType),
                runtimeEvent.message(),
                frontendPayload(runtimeEvent)
        );

        if (eventService.persist(draft).isEmpty()) {
            return false;
        }

        switch (runtimeEvent.type()) {
            case STEP_STARTED -> createRunningStep(runtimeEvent);
            case STEP_COMPLETED, STEP_FAILED -> completeStep(runtimeEvent);
            case ACTION_RECOMMENDED -> createRecommendedAction(runtimeEvent);
            case RUN_COMPLETED -> completeRun(runtimeEvent);
            case RUN_FAILED -> failRun(runtimeEvent);
            default -> {
                // planner/tool/observation 等事件只做审计和前端展示。
            }
        }
        return true;
    }

    /** Java 在 stream 异常或无 terminal 时合成唯一失败事件。 */
    @Transactional
    public void markStreamFailed(String runCode, String errorMessage) {
        AgentRunEntity run = requireRun(runCode);
        if (run.getStatus() == AgentRunStatus.SUCCEEDED
                || run.getStatus() == AgentRunStatus.FAILED
                || run.getStatus() == AgentRunStatus.WAITING_CONFIRMATION) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("pythonEventType", "JAVA_SYNTHETIC_RUN_FAILED");
        payload.put("errorMessage", errorMessage);
        AgentRunEventDraft draft = new AgentRunEventDraft(
                null,
                runCode,
                null,
                AgentRunEventType.RUN_FAILED,
                null,
                null,
                AgentRunStatus.FAILED.name(),
                errorMessage,
                writeJson(payload)
        );
        if (eventService.persist(draft).isEmpty()) {
            return;
        }
        run.setStatus(AgentRunStatus.FAILED);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(OffsetDateTime.now());
        runRepository.updateById(run);
    }

    /** Python RUN_COMPLETED 先按 Java action 状态规范化成唯一前端 terminal。 */
    private AgentRunEventType normalizeEventType(AgentRuntimeEvent event) {
        if (event.type() == AgentRuntimeEventType.RUN_COMPLETED) {
            return actionRepository.existsPendingConfirmation(event.runCode())
                    ? AgentRunEventType.RUN_WAITING_CONFIRMATION
                    : AgentRunEventType.RUN_COMPLETED;
        }
        return AgentRunEventType.valueOf(event.type().name());
    }

    /** terminal 的 status 必须使用 Java 最终业务状态。 */
    private String normalizeStatus(AgentRuntimeEvent event, AgentRunEventType frontendType) {
        return switch (frontendType) {
            case RUN_COMPLETED -> AgentRunStatus.SUCCEEDED.name();
            case RUN_FAILED -> AgentRunStatus.FAILED.name();
            case RUN_WAITING_CONFIRMATION -> AgentRunStatus.WAITING_CONFIRMATION.name();
            default -> event.status();
        };
    }

    /** 创建 RUNNING step，stepCode 仍由 Java 生成。 */
    private void createRunningStep(AgentRuntimeEvent event) {
        requireNodeInvocation(event);
        if (stepRepository.findByRunCodeAndNodeInvocationId(
                event.runCode(), event.nodeInvocationId()).isPresent()) {
            return;
        }
        AgentStepEntity step = new AgentStepEntity();
        step.setId(idGenerator.nextId());
        step.setRunCode(event.runCode());
        step.setNodeInvocationId(event.nodeInvocationId());
        step.setStepCode(idGenerator.nextId(STEP_CODE_PREFIX));
        step.setNodeName(requireText(event.nodeName(), "nodeName"));
        step.setToolName(event.toolName());
        step.setStepType(AgentStepType.NODE);
        step.setStatus(AgentStepStatus.RUNNING);
        step.setStartedAt(OffsetDateTime.now());
        stepRepository.insert(step);
    }

    /** 按 correlation id 精确完成或失败对应 step。 */
    private void completeStep(AgentRuntimeEvent event) {
        requireNodeInvocation(event);
        AgentStepEntity step = stepRepository.findByRunCodeAndNodeInvocationId(
                        event.runCode(), event.nodeInvocationId())
                .orElseThrow(() -> new BusinessException(
                        "Agent step not found for nodeInvocationId: " + event.nodeInvocationId()));
        JsonNode payload = event.payload();
        step.setNodeName(event.nodeName() == null ? step.getNodeName() : event.nodeName());
        step.setToolName(event.toolName() == null ? step.getToolName() : event.toolName());
        step.setStepType(enumValue(
                AgentStepType.class,
                text(payload, "stepType"),
                step.getStepType()
        ));
        step.setStatus(enumValue(
                AgentStepStatus.class,
                event.status(),
                event.type() == AgentRuntimeEventType.STEP_FAILED
                        ? AgentStepStatus.FAILED
                        : AgentStepStatus.SUCCEEDED
        ));
        step.setInputJson(text(payload, "inputJson"));
        step.setOutputJson(text(payload, "outputJson"));
        step.setDurationMs(longValue(payload, "durationMs"));
        step.setErrorMessage(event.type() == AgentRuntimeEventType.STEP_FAILED
                ? firstText(text(payload, "errorMessage"), event.message())
                : text(payload, "errorMessage"));
        step.setFinishedAt(OffsetDateTime.now());
        stepRepository.updateById(step);
    }

    /** 按 Java action catalog 创建待确认动作。 */
    private void createRecommendedAction(AgentRuntimeEvent event) {
        JsonNode payload = requireObjectPayload(event);
        String toolName = requireText(event.toolName(), "toolName");
        RecommendedActionCatalog.RecommendedActionDefinition definition = actionCatalog.find(toolName)
                .orElseThrow(() -> new BusinessException(
                        "Agent recommended action is not in catalog: " + toolName));
        AgentActionEntity action = new AgentActionEntity();
        action.setId(idGenerator.nextId());
        action.setRunCode(event.runCode());
        action.setActionCode(idGenerator.nextId(ACTION_CODE_PREFIX));
        action.setToolName(toolName);
        action.setTitle(requireText(text(payload, "title"), "payload.title"));
        action.setReason(text(payload, "reason"));
        action.setRiskLevel(definition.riskLevel());
        action.setRequiresConfirmation(definition.requiresConfirmation());
        action.setStatus(AgentActionStatus.PENDING_CONFIRMATION);
        action.setActionPayload(text(payload, "actionPayload"));
        actionRepository.insert(action);
    }

    /** 使用 Java pending action 判断成功或等待确认。 */
    private void completeRun(AgentRuntimeEvent event) {
        AgentRunEntity run = requireRun(event.runCode());
        boolean waitingConfirmation = actionRepository.existsPendingConfirmation(event.runCode());
        run.setStatus(waitingConfirmation
                ? AgentRunStatus.WAITING_CONFIRMATION
                : AgentRunStatus.SUCCEEDED);
        run.setSummary(text(event.payload(), "summary"));
        run.setErrorMessage(null);
        run.setFinishedAt(waitingConfirmation ? null : OffsetDateTime.now());
        runRepository.updateById(run);
    }

    /** 应用 Python RUN_FAILED。 */
    private void failRun(AgentRuntimeEvent event) {
        AgentRunEntity run = requireRun(event.runCode());
        String errorMessage = firstText(
                text(event.payload(), "errorMessage"),
                event.message()
        );
        run.setStatus(AgentRunStatus.FAILED);
        run.setSummary(text(event.payload(), "summary"));
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(OffsetDateTime.now());
        runRepository.updateById(run);
    }

    /** payload 保留 Python 原始事件类型和业务 payload。 */
    private String frontendPayload(AgentRuntimeEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("pythonEventType", event.type().name());
        payload.set("runtimePayload",
                event.payload() == null ? objectMapper.createObjectNode() : event.payload());
        if (event.createdAt() != null) {
            payload.put("runtimeCreatedAt", event.createdAt().toString());
        }
        return writeJson(payload);
    }

    /** 校验 terminal 标记和类型一致。 */
    private void validateRuntimeEvent(AgentRuntimeEvent event) {
        requireText(event.eventId(), "eventId");
        requireText(event.runCode(), "runCode");
        boolean terminalType = event.type() == AgentRuntimeEventType.RUN_COMPLETED
                || event.type() == AgentRuntimeEventType.RUN_FAILED;
        if (terminalType != event.terminal()) {
            throw new BusinessException("Agent Runtime terminal flag does not match event type");
        }
    }

    /** 判断 run 是否已经进入 Java 权威终态。 */
    private boolean isTerminalRunStatus(AgentRunStatus status) {
        return status == AgentRunStatus.SUCCEEDED
                || status == AgentRunStatus.FAILED
                || status == AgentRunStatus.WAITING_CONFIRMATION;
    }

    /** node step 事件必须携带 correlation id。 */
    private void requireNodeInvocation(AgentRuntimeEvent event) {
        requireText(event.nodeInvocationId(), "nodeInvocationId");
    }

    /** 读取 run。 */
    private AgentRunEntity requireRun(String runCode) {
        return runRepository.findByRunCode(runCode)
                .orElseThrow(() -> new BusinessException("Agent run not found: " + runCode));
    }

    /** 要求 payload 是对象。 */
    private JsonNode requireObjectPayload(AgentRuntimeEvent event) {
        if (event.payload() == null || !event.payload().isObject()) {
            throw new BusinessException("Agent Runtime event payload must be an object");
        }
        return event.payload();
    }

    /** 读取 JSON 文本字段。 */
    private String text(JsonNode payload, String fieldName) {
        if (payload == null) {
            return null;
        }
        JsonNode value = payload.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 读取 JSON long 字段。 */
    private Long longValue(JsonNode payload, String fieldName) {
        if (payload == null) {
            return null;
        }
        JsonNode value = payload.get(fieldName);
        return value == null || value.isNull() || !value.canConvertToLong()
                ? null
                : value.longValue();
    }

    /** 安全读取枚举，空值时使用 fallback。 */
    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported " + type.getSimpleName() + ": " + value);
        }
    }

    /** 返回第一个非空白文本。 */
    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    /** 要求文本非空白。 */
    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Agent Runtime event " + fieldName + " must not be blank");
        }
        return value;
    }

    /** 序列化 JSON。 */
    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize Agent Runtime event payload: " + ex.getMessage());
        }
    }
}
