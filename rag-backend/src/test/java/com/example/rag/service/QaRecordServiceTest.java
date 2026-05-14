package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RagQaProperties;
import com.example.rag.model.dto.QaHistoryRecordView;
import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.PageResponse;
import com.example.rag.model.response.QaAnswerResponse;
import com.example.rag.model.response.QaSourceResponse;
import com.example.rag.model.response.QaHistoryRecordResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import com.example.rag.persistence.ChatMessageRepository;
import com.example.rag.persistence.ChatSessionRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.ChatMessageEntity;
import com.example.rag.persistence.entity.ChatSessionEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 问答记录持久化服务测试。 */
@ExtendWith(MockitoExtension.class)
class QaRecordServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Captor
    private ArgumentCaptor<ChatSessionEntity> sessionCaptor;

    @Captor
    private ArgumentCaptor<ChatMessageEntity> messageCaptor;

    @Test
    void persistShouldUseConfiguredQaProperties() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("day18-kb");

        RagQaProperties qaProperties = new RagQaProperties();
        qaProperties.setDefaultCreatedBy("ops-bot");
        qaProperties.setMessageType("QA_AUDIT");
        qaProperties.setPromptTemplate("qa-config-v2");
        qaProperties.setSessionNameMaxLength(12);

        when(knowledgeBaseRepository.findByCode("day18-kb")).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(1L, 2L);
        when(snowflakeIdGenerator.nextId("SES-")).thenReturn("SES-1");
        when(snowflakeIdGenerator.nextId("MSG-")).thenReturn("MSG-1");

        QaRecordService service = new QaRecordService(
                knowledgeBaseRepository,
                chatSessionRepository,
                chatMessageRepository,
                snowflakeIdGenerator,
                new ObjectMapper(),
                qaProperties
        );

        QaAnswerResponse answerResponse = new QaAnswerResponse(
                "这是一个超过十二个字符的问题文本",
                "回答内容",
                3,
                "deepseek-v4-pro",
                RetrievalMode.DENSE,
                "NONE",
                List.of(new RetrievedChunkResponse(
                        1L,
                        2L,
                        "DOC-1",
                        "文档一",
                        0,
                        "TEXT",
                        "chunk content",
                        0,
                        20,
                        "bge-small-zh-v1.5",
                        0.91D
                )),
                List.of(new QaSourceResponse(
                        "DOC-1",
                        "文档一",
                        1L,
                        0,
                        "chunk content",
                        0.91D,
                        0,
                        20
                ))
        );

        service.persist("day18-kb", answerResponse, 120L);

        verify(chatSessionRepository).insert(sessionCaptor.capture());
        verify(chatMessageRepository).insert(messageCaptor.capture());

        assertThat(sessionCaptor.getValue().getCreatedBy()).isEqualTo("ops-bot");
        assertThat(sessionCaptor.getValue().getSessionName()).isEqualTo("这是一个超过十二个字符的");
        assertThat(messageCaptor.getValue().getMessageType()).isEqualTo("QA_AUDIT");
        assertThat(messageCaptor.getValue().getPromptTemplate()).isEqualTo("qa-config-v2");
        assertThat(messageCaptor.getValue().getRetrievedChunks()).contains("\"retrievalMode\":\"DENSE\"");
        assertThat(messageCaptor.getValue().getRetrievedChunks()).contains("\"fusionStrategy\":\"NONE\"");
        assertThat(messageCaptor.getValue().getRetrievedChunks()).contains("\"documentCode\":\"DOC-1\"");
        assertThat(messageCaptor.getValue().getSources()).contains("\"chunkId\":\"1\"");
    }

    @Test
    void listHistoryShouldReadRetrievalModeAndFusionStrategyFromSnapshot() throws JsonProcessingException {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("day24-kb");

        ObjectMapper objectMapper = new ObjectMapper();
        QaHistoryRecordView view = new QaHistoryRecordView();
        view.setSessionCode("SES-1");
        view.setSessionName("问题一");
        view.setMessageCode("MSG-1");
        view.setQuestion("问题一");
        view.setAnswer("回答一");
        view.setModelName("deepseek-v4-pro");
        view.setTopK(3);
        view.setLatencyMs(120L);
        view.setPromptTemplate("qa-default-v1");
        view.setRetrievedChunks(objectMapper.writeValueAsString(java.util.Map.of(
                "retrievalMode", "HYBRID",
                "fusionStrategy", "RRF",
                "chunks", List.of(new RetrievedChunkResponse(
                        1L,
                        2L,
                        "DOC-1",
                        "文档一",
                        0,
                        "TEXT",
                        "chunk content",
                        0,
                        20,
                        "bge-small-zh-v1.5",
                        0.032D
                ))
        )));
        view.setSources(objectMapper.writeValueAsString(List.of(new QaSourceResponse(
                "DOC-1",
                "文档一",
                1L,
                0,
                "chunk content",
                0.032D,
                0,
                20
        ))));
        view.setCreatedAt(OffsetDateTime.parse("2026-05-14T12:00:00+08:00"));

        when(knowledgeBaseRepository.findByCode("day24-kb")).thenReturn(Optional.of(knowledgeBase));
        when(chatMessageRepository.pageByKnowledgeBase(any())).thenReturn(new PageResult<>(List.of(view), 1, 1, 20));

        QaRecordService service = new QaRecordService(
                knowledgeBaseRepository,
                chatSessionRepository,
                chatMessageRepository,
                snowflakeIdGenerator,
                objectMapper,
                new RagQaProperties()
        );

        PageResponse<QaHistoryRecordResponse> response = service.listHistory("day24-kb", 1L, 20L);

        assertThat(response.records()).hasSize(1);
        assertThat(response.records().get(0).retrievalMode()).isEqualTo(RetrievalMode.HYBRID);
        assertThat(response.records().get(0).fusionStrategy()).isEqualTo("RRF");
        assertThat(response.records().get(0).retrievalResults()).hasSize(1);
    }

    @Test
    void listHistoryShouldRemainCompatibleWithLegacyRetrievedChunksArray() throws JsonProcessingException {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("day24-kb");

        ObjectMapper objectMapper = new ObjectMapper();
        QaHistoryRecordView view = new QaHistoryRecordView();
        view.setSessionCode("SES-1");
        view.setSessionName("问题一");
        view.setMessageCode("MSG-1");
        view.setQuestion("问题一");
        view.setAnswer("回答一");
        view.setModelName("deepseek-v4-pro");
        view.setTopK(3);
        view.setLatencyMs(120L);
        view.setPromptTemplate("qa-default-v1");
        view.setRetrievedChunks(objectMapper.writeValueAsString(List.of(new RetrievedChunkResponse(
                1L,
                2L,
                "DOC-1",
                "文档一",
                0,
                "TEXT",
                "chunk content",
                0,
                20,
                "bge-small-zh-v1.5",
                0.91D
        ))));
        view.setSources(objectMapper.writeValueAsString(List.of()));
        view.setCreatedAt(OffsetDateTime.parse("2026-05-14T12:00:00+08:00"));

        when(knowledgeBaseRepository.findByCode("day24-kb")).thenReturn(Optional.of(knowledgeBase));
        when(chatMessageRepository.pageByKnowledgeBase(any())).thenReturn(new PageResult<>(List.of(view), 1, 1, 20));

        QaRecordService service = new QaRecordService(
                knowledgeBaseRepository,
                chatSessionRepository,
                chatMessageRepository,
                snowflakeIdGenerator,
                objectMapper,
                new RagQaProperties()
        );

        PageResponse<QaHistoryRecordResponse> response = service.listHistory("day24-kb", 1L, 20L);

        assertThat(response.records()).hasSize(1);
        assertThat(response.records().get(0).retrievalMode()).isEqualTo(RetrievalMode.DENSE);
        assertThat(response.records().get(0).fusionStrategy()).isEqualTo("NONE");
        assertThat(response.records().get(0).retrievalResults()).hasSize(1);
    }
}
