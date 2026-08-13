package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.entity.AgentActionEntity;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.AgentStepEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将旧 JSON Runtime 已落库结果转换成只读展示事件。
 *
 * <p>该转换器不创建或修改 step/action，避免 compatibility 阶段重复落业务数据。</p>
 */
@Component
public class AgentRunCompatibilityEventConverter {
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentActionRepository agentActionRepository;
    private final AgentRunEventService eventService;
    private final ObjectMapper objectMapper;

    /** 构造 AgentRunCompatibilityEventConverter。 */
    public AgentRunCompatibilityEventConverter(AgentRunRepository agentRunRepository,
                                               AgentStepRepository agentStepRepository,
                                               AgentActionRepository agentActionRepository,
                                               AgentRunEventService eventService,
                                               ObjectMapper objectMapper) {
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.agentActionRepository = agentActionRepository;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    /** 发布 run 已创建事件。 */
    public void publishRunStarted(AgentRunEntity run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "COMPATIBILITY");
        payload.put("goal", run.getGoal());
        eventService.persist(new AgentRunEventDraft(
                run.getRunCode() + "-RUN-STARTED",
                run.getRunCode(),
                null,
                AgentRunEventType.RUN_STARTED,
                null,
                null,
                AgentRunStatus.RUNNING.name(),
                "Agent run 已创建，正在后台执行",
                toJson(payload)
        ));
    }

    /** 把旧 JSON 执行完成后的正式数据库结果转换成 SSE 展示事件。 */
    public void publishPersistedResult(String runCode) {
        AgentRunEntity run = agentRunRepository.findByRunCode(runCode)
                .orElseThrow(() -> new BusinessException("Agent run not found: " + runCode));

        for (AgentStepEntity step : agentStepRepository.findByRunCode(runCode)) {
            publishStep(step);
        }
        for (AgentActionEntity action : agentActionRepository.findByRunCode(runCode)) {
            publishAction(action);
        }
        publishTerminal(run);
    }

    /** 发布一个已落库 step 的兼容事件。 */
    private void publishStep(AgentStepEntity step) {
        AgentRunEventType type = step.getStatus() == AgentStepStatus.FAILED
                ? AgentRunEventType.STEP_FAILED
                : AgentRunEventType.STEP_COMPLETED;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "COMPATIBILITY");
        payload.put("stepCode", step.getStepCode());
        payload.put("stepType", step.getStepType());
        payload.put("inputJson", step.getInputJson());
        payload.put("outputJson", step.getOutputJson());
        payload.put("durationMs", step.getDurationMs());
        payload.put("errorMessage", step.getErrorMessage());
        eventService.persist(new AgentRunEventDraft(
                step.getStepCode() + "-COMPAT",
                step.getRunCode(),
                null,
                type,
                step.getNodeName(),
                step.getToolName(),
                step.getStatus().name(),
                step.getErrorMessage() == null
                        ? step.getNodeName() + " 执行完成"
                        : step.getErrorMessage(),
                toJson(payload)
        ));
    }

    /** 发布一个已落库推荐动作的兼容事件。 */
    private void publishAction(AgentActionEntity action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "COMPATIBILITY");
        payload.put("actionCode", action.getActionCode());
        payload.put("title", action.getTitle());
        payload.put("reason", action.getReason());
        payload.put("riskLevel", action.getRiskLevel());
        payload.put("requiresConfirmation", action.getRequiresConfirmation());
        payload.put("actionPayload", action.getActionPayload());
        eventService.persist(new AgentRunEventDraft(
                action.getActionCode() + "-COMPAT",
                action.getRunCode(),
                null,
                AgentRunEventType.ACTION_RECOMMENDED,
                null,
                action.getToolName(),
                action.getStatus().name(),
                action.getTitle(),
                toJson(payload)
        ));
    }

    /** 根据 Java 最终 run.status 生成唯一终态事件。 */
    private void publishTerminal(AgentRunEntity run) {
        AgentRunEventType terminalType = switch (run.getStatus()) {
            case SUCCEEDED -> AgentRunEventType.RUN_COMPLETED;
            case FAILED -> AgentRunEventType.RUN_FAILED;
            case WAITING_CONFIRMATION -> AgentRunEventType.RUN_WAITING_CONFIRMATION;
            case QUEUED, RUNNING -> throw new BusinessException("Agent run is not terminal: " + run.getRunCode());
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "COMPATIBILITY");
        payload.put("runStatus", run.getStatus());
        payload.put("summary", run.getSummary());
        payload.put("errorMessage", run.getErrorMessage());
        eventService.persist(new AgentRunEventDraft(
                run.getRunCode() + "-TERMINAL",
                run.getRunCode(),
                null,
                terminalType,
                null,
                null,
                run.getStatus().name(),
                run.getErrorMessage() == null ? run.getSummary() : run.getErrorMessage(),
                toJson(payload)
        ));
    }

    /** 序列化兼容事件 payload。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize agent compatibility event: " + ex.getMessage());
        }
    }
}
