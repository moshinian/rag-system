package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.dto.QaHistoryRecordView;
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
 * Day 13 问答记录持久化服务。
 */
@Service
public class QaRecordService {

    private static final long DEFAULT_PAGE_NO = 1;
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_CREATED_BY = "qa-service";
    private static final String MESSAGE_TYPE_QA = "QA";
    private static final String PROMPT_TEMPLATE_V1 = "qa-default-v1";

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    public QaRecordService(KnowledgeBaseRepository knowledgeBaseRepository,
                           ChatSessionRepository chatSessionRepository,
                           ChatMessageRepository chatMessageRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           ObjectMapper objectMapper) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
    }

    /** 保存一次问答记录。 */
    @Transactional
    public QaPersistenceResult persist(String kbCode, QaAnswerResponse answerResponse, Long latencyMs) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        long sessionId = snowflakeIdGenerator.nextId();
        String sessionCode = snowflakeIdGenerator.nextId("SES-");
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        session.setSessionCode(sessionCode);
        session.setKnowledgeBaseId(knowledgeBase.getId());
        session.setSessionName(buildSessionName(answerResponse.question()));
        session.setCreatedBy(DEFAULT_CREATED_BY);
        chatSessionRepository.insert(session);

        long messageId = snowflakeIdGenerator.nextId();
        String messageCode = snowflakeIdGenerator.nextId("MSG-");
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(messageId);
        message.setMessageCode(messageCode);
        message.setSessionId(sessionId);
        message.setMessageType(MESSAGE_TYPE_QA);
        message.setQuestion(answerResponse.question());
        message.setAnswer(answerResponse.answer());
        message.setRetrievedChunks(toJson(answerResponse.retrievalResults()));
        message.setSources(toJson(answerResponse.sources()));
        message.setPromptTemplate(PROMPT_TEMPLATE_V1);
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

    private QaHistoryRecordResponse toHistoryResponse(QaHistoryRecordView view) {
        return new QaHistoryRecordResponse(
                view.getSessionCode(),
                view.getSessionName(),
                view.getMessageCode(),
                view.getQuestion(),
                view.getAnswer(),
                view.getModelName(),
                view.getTopK(),
                view.getLatencyMs(),
                view.getPromptTemplate(),
                fromRetrievedChunksJson(view.getRetrievedChunks()),
                fromSourcesJson(view.getSources()),
                view.getCreatedAt()
        );
    }

    private String buildSessionName(String question) {
        String normalized = question == null ? "" : question.trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize QA record payload: " + ex.getMessage());
        }
    }

    private List<RetrievedChunkResponse> fromRetrievedChunksJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<RetrievedChunkResponse>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to deserialize retrieved chunks: " + ex.getMessage());
        }
    }

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

    private long normalizePageNo(Long pageNo) {
        if (pageNo == null) {
            return DEFAULT_PAGE_NO;
        }
        if (pageNo < 1) {
            throw new BusinessException("Page number must be greater than 0");
        }
        return pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    public record QaPersistenceResult(
            String sessionCode,
            String messageCode
    ) {
    }
}
