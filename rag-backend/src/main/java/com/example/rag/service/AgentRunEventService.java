package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.persistence.AgentRunEventRepository;
import com.example.rag.persistence.entity.AgentRunEventEntity;
import com.example.rag.service.event.AgentRunEventCommittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agent 运行事件持久化与查询服务。
 */
@Service
public class AgentRunEventService {
    private static final String EVENT_CODE_PREFIX = "EVT-";

    private final AgentRunEventRepository eventRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ApplicationEventPublisher applicationEventPublisher;

    /** 构造 AgentRunEventService。 */
    public AgentRunEventService(AgentRunEventRepository eventRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                ApplicationEventPublisher applicationEventPublisher) {
        this.eventRepository = eventRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 在事务中持久化事件，并注册事务提交后的 SSE 发布通知。
     *
     * <p>重复 eventCode 不会再次发布，也不会触发后续领域状态应用。</p>
     */
    @Transactional
    public Optional<AgentRunEventResponse> persist(AgentRunEventDraft draft) {
        OffsetDateTime now = OffsetDateTime.now();
        AgentRunEventEntity entity = new AgentRunEventEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setEventCode(resolveEventCode(draft.eventCode()));
        entity.setRunCode(draft.runCode());
        entity.setNodeInvocationId(draft.nodeInvocationId());
        entity.setEventType(draft.eventType());
        entity.setNodeName(draft.nodeName());
        entity.setToolName(draft.toolName());
        entity.setStatus(draft.status());
        entity.setMessage(draft.message());
        entity.setPayloadJson(draft.payloadJson());
        entity.setTerminal(draft.eventType().isTerminal());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        if (!eventRepository.insertIgnore(entity)) {
            return Optional.empty();
        }

        AgentRunEventResponse response = toResponse(entity);
        // 这里只发布 Spring 事务事件；真正的 SseEmitter 发送发生在 AFTER_COMMIT listener。
        applicationEventPublisher.publishEvent(new AgentRunEventCommittedEvent(response));
        return Optional.of(response);
    }

    /** 查询 run 的全部事件。 */
    @Transactional(readOnly = true)
    public List<AgentRunEventResponse> findAllEvents(String runCode) {
        return eventRepository.findByRunCode(runCode).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 根据 Last-Event-ID 查询后续事件。
     *
     * <p>补发顺序使用数据库 id，不依赖事件生产方时间。</p>
     */
    @Transactional(readOnly = true)
    public List<AgentRunEventResponse> findEventsAfter(String runCode, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return findAllEvents(runCode);
        }
        AgentRunEventEntity lastEvent = eventRepository.findByEventCode(lastEventId.trim())
                .orElseThrow(() -> new BusinessException("Agent run event not found: " + lastEventId));
        if (!runCode.equals(lastEvent.getRunCode())) {
            throw new BusinessException("Agent run event does not belong to run: " + lastEventId);
        }
        return eventRepository.findAfterId(runCode, lastEvent.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 将持久化实体转换成 SSE 响应。 */
    private AgentRunEventResponse toResponse(AgentRunEventEntity entity) {
        return new AgentRunEventResponse(
                entity.getId(),
                entity.getEventCode(),
                entity.getRunCode(),
                entity.getEventType(),
                entity.getNodeInvocationId(),
                entity.getNodeName(),
                entity.getToolName(),
                entity.getStatus(),
                entity.getMessage(),
                entity.getPayloadJson(),
                Boolean.TRUE.equals(entity.getTerminal()),
                entity.getCreatedAt()
        );
    }

    /** 优先使用上游稳定 eventCode，空值时由 Java 生成。 */
    private String resolveEventCode(String eventCode) {
        return eventCode == null || eventCode.isBlank()
                ? snowflakeIdGenerator.nextId(EVENT_CODE_PREFIX)
                : eventCode.trim();
    }
}
