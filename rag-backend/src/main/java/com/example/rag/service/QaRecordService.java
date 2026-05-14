package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RagQaProperties;
import com.example.rag.model.dto.QaHistoryRecordView;
import com.example.rag.model.dto.RetrievedChunksSnapshot;
import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.PageResponse;
import com.example.rag.model.response.QaAnswerResponse;
import com.example.rag.model.response.QaHistoryRecordResponse;
import com.example.rag.model.response.QaSourceResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import com.example.rag.persistence.ChatMessageRepository;
import com.example.rag.persistence.ChatSessionRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.ChatMessageEntity;
import com.example.rag.persistence.entity.ChatSessionEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.PageResult;
import com.example.rag.persistence.query.QaHistoryPageQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 问答记录持久化服务。
 *
 * 负责保存问答会话和消息，并提供历史查询能力。
 */
@Service
public class QaRecordService {
    private static final long DEFAULT_PAGE_NO = 1;
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;
    private final RagQaProperties ragQaProperties;

    /** 构造QaRecordService。 */
    public QaRecordService(KnowledgeBaseRepository knowledgeBaseRepository,
                           ChatSessionRepository chatSessionRepository,
                           ChatMessageRepository chatMessageRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           ObjectMapper objectMapper,
                           RagQaProperties ragQaProperties) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
        this.ragQaProperties = ragQaProperties;
    }

    /** 保存一次问答记录。 */
    @Transactional
    public QaPersistenceResult persist(String kbCode, QaAnswerResponse answerResponse, Long latencyMs) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        // 当前实现按单次问答创建独立 session，便于后续演进为会话复用。
        long sessionId = snowflakeIdGenerator.nextId();
        String sessionCode = snowflakeIdGenerator.nextId("SES-");
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        session.setSessionCode(sessionCode);
        session.setKnowledgeBaseId(knowledgeBase.getId());
        session.setSessionName(buildSessionName(answerResponse.question()));
        session.setCreatedBy(defaultCreatedBy());
        chatSessionRepository.insert(session);

        long messageId = snowflakeIdGenerator.nextId();
        String messageCode = snowflakeIdGenerator.nextId("MSG-");
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(messageId);
        message.setMessageCode(messageCode);
        message.setSessionId(sessionId);
        message.setMessageType(messageType());
        message.setQuestion(answerResponse.question());
        message.setAnswer(answerResponse.answer());
        // retrievalResults 与检索模式一起按快照持久化，避免后续历史回放无法判断当时跑的是 dense 还是 hybrid。
        message.setRetrievedChunks(toJson(new RetrievedChunksSnapshot(
                answerResponse.retrievalMode(),
                answerResponse.fusionStrategy(),
                answerResponse.retrievalResults()
        )));
        message.setSources(toJson(answerResponse.sources()));
        message.setPromptTemplate(promptTemplate());
        message.setModelName(answerResponse.chatModel());
        message.setTopK(answerResponse.topK());
        message.setLatencyMs(latencyMs);
        chatMessageRepository.insert(message);

        return new QaPersistenceResult(sessionCode, messageCode);
    }

    /** 按知识库分页查询问答历史。 */
    @Transactional(readOnly = true)
    public PageResponse<QaHistoryRecordResponse> listHistory(String kbCode, Long pageNo, Long pageSize) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        PageResult<QaHistoryRecordView> page = chatMessageRepository.pageByKnowledgeBase(
                new QaHistoryPageQuery(
                        knowledgeBase.getId(),
                        normalizePageNo(pageNo),
                        normalizePageSize(pageSize)
                )
        );
        return new PageResponse<>(
                page.records().stream().map(this::toHistoryResponse).toList(),
                page.total(),
                page.pageNo(),
                page.pageSize()
        );
    }

    /** 把历史查询视图对象映射成接口返回结构。 */
    private QaHistoryRecordResponse toHistoryResponse(QaHistoryRecordView view) {
        RetrievedChunksSnapshot retrievalSnapshot = retrievalSnapshot(view.getRetrievedChunks());
        return new QaHistoryRecordResponse(
                view.getSessionCode(),
                view.getSessionName(),
                view.getMessageCode(),
                view.getQuestion(),
                view.getAnswer(),
                view.getModelName(),
                view.getTopK(),
                retrievalSnapshot.retrievalMode(),
                retrievalSnapshot.fusionStrategy(),
                view.getLatencyMs(),
                view.getPromptTemplate(),
                retrievalSnapshot.chunks(),
                fromSourcesJson(view.getSources()),
                view.getCreatedAt()
        );
    }

    /** 用问题前缀构造会话名，并限制最大长度。 */
    private String buildSessionName(String question) {
        String normalized = question == null ? "" : question.trim();
        if (normalized.length() <= sessionNameMaxLength()) {
            return normalized;
        }
        return normalized.substring(0, sessionNameMaxLength());
    }

    /** 序列化扩展字段，避免持久层直接依赖复杂对象结构。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize QA record payload: " + ex.getMessage());
        }
    }

    /** 反序列化历史记录中的召回快照，兼容老数组格式与新对象格式。 */
    private RetrievedChunksSnapshot retrievalSnapshot(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new RetrievedChunksSnapshot(RetrievalMode.DENSE, "NONE", List.of());
            }
            String normalizedJson = json.trim();
            if (normalizedJson.startsWith("[")) {
                List<RetrievedChunkResponse> chunks = objectMapper.readValue(
                        normalizedJson,
                        new TypeReference<List<RetrievedChunkResponse>>() {
                        }
                );
                return new RetrievedChunksSnapshot(RetrievalMode.DENSE, "NONE", chunks);
            }
            RetrievedChunksSnapshot snapshot = objectMapper.readValue(normalizedJson, new TypeReference<RetrievedChunksSnapshot>() {
            });
            RetrievalMode retrievalMode = snapshot.retrievalMode() == null ? RetrievalMode.DENSE : snapshot.retrievalMode();
            String fusionStrategy = snapshot.fusionStrategy() == null || snapshot.fusionStrategy().isBlank()
                    ? "NONE"
                    : snapshot.fusionStrategy();
            List<RetrievedChunkResponse> chunks = snapshot.chunks() == null ? List.of() : snapshot.chunks();
            return new RetrievedChunksSnapshot(retrievalMode, fusionStrategy, chunks);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to deserialize retrieval snapshot: " + ex.getMessage());
        }
    }

    /** 反序列化历史记录中的来源列表。 */
    private List<QaSourceResponse> fromSourcesJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<QaSourceResponse>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to deserialize QA sources: " + ex.getMessage());
        }
    }

    /** 归一化页码并执行边界校验。 */
    private long normalizePageNo(Long pageNo) {
        if (pageNo == null) {
            return DEFAULT_PAGE_NO;
        }
        if (pageNo < 1) {
            throw new BusinessException("Page number must be greater than 0");
        }
        return pageNo;
    }

    /** 归一化分页大小并执行边界校验。 */
    private long normalizePageSize(Long pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    /** 持久化后的最小定位信息，供调用方关联刚写入的记录。 */
    public record QaPersistenceResult(
            String sessionCode,
            String messageCode
    ) {
    }

    /** 读取默认创建人配置，没有配置时回退到安全默认值。 */
    private String defaultCreatedBy() {
        String configured = ragQaProperties.getDefaultCreatedBy();
        return configured == null || configured.isBlank() ? "qa-service" : configured.trim();
    }

    /** 读取消息类型配置，没有配置时回退到安全默认值。 */
    private String messageType() {
        String configured = ragQaProperties.getMessageType();
        return configured == null || configured.isBlank() ? "QA" : configured.trim();
    }

    /** 读取提示词模板标识，没有配置时回退到安全默认值。 */
    private String promptTemplate() {
        String configured = ragQaProperties.getPromptTemplate();
        return configured == null || configured.isBlank() ? "qa-default-v1" : configured.trim();
    }

    /** 读取会话名最大长度，并兜底到合理的最小阈值。 */
    private int sessionNameMaxLength() {
        Integer configured = ragQaProperties.getSessionNameMaxLength();
        return configured == null || configured < 10 ? 80 : configured;
    }
}
