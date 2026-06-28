package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Agent run Recovery 单条事务处理。
 */
@Service
public class AgentRunRecoveryService {
    static final String RECOVERY_ERROR_MESSAGE =
            "Agent run recovery timeout: no runtime heartbeat or business event received for configured idle timeout.";

    private static final Logger log = LoggerFactory.getLogger(AgentRunRecoveryService.class);

    private final AgentRunRepository runRepository;
    private final AgentRunEventService eventService;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /** 构造 AgentRunRecoveryService。 */
    public AgentRunRecoveryService(AgentRunRepository runRepository,
                                   AgentRunEventService eventService,
                                   SnowflakeIdGenerator idGenerator,
                                   ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.eventService = eventService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 在同一事务内完成 run 失败更新和 terminal event 写入。
     *
     * <p>如果 event 写入失败，事务回滚，避免 run 已 FAILED 但没有 terminal event。</p>
     */
    @Transactional
    public boolean recoverOne(AgentRunEntity run, OffsetDateTime idleCutoff) {
        int updated = runRepository.markRunningRunFailedByRecovery(
                run.getRunCode(),
                idleCutoff,
                RECOVERY_ERROR_MESSAGE
        );
        if (updated != 1) {
            log.info(StructuredLogMessage.of("agent.run.recovery.skipped")
                    .field("runCode", run.getRunCode())
                    .field("reason", "conditional_update_missed")
                    .build());
            return false;
        }

        String eventCode = run.getRunCode() + "-J-RECOVERY-" + idGenerator.nextId();
        AgentRunEventDraft draft = new AgentRunEventDraft(
                eventCode,
                run.getRunCode(),
                null,
                AgentRunEventType.RUN_FAILED,
                null,
                null,
                AgentRunStatus.FAILED.name(),
                RECOVERY_ERROR_MESSAGE,
                recoveryPayload()
        );
        if (eventService.persist(draft).isEmpty()) {
            throw new BusinessException("Agent recovery event already exists: " + eventCode);
        }

        log.warn(StructuredLogMessage.of("agent.run.recovery.failed")
                .field("runCode", run.getRunCode())
                .field("eventCode", eventCode)
                .build());
        return true;
    }

    /** 构造 Recovery 事件 payload。 */
    private String recoveryPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source", "JAVA_RECOVERY");
        payload.put("reason", "RUNNING_TIMEOUT");
        payload.put("message", RECOVERY_ERROR_MESSAGE);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize Agent recovery payload: " + ex.getMessage());
        }
    }
}
