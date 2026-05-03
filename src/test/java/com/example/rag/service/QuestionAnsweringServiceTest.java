package com.example.rag.service;

import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.integration.llm.OpenAiCompatibleClient;
import com.example.rag.model.dto.RetrievedChunkCandidate;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAnsweringServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private OpenAiCompatibleClient openAiCompatibleClient;

    private QuestionAnsweringService questionAnsweringService;

    @BeforeEach
    void setUp() {
        RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
        embeddingProperties.setProvider("openai-compatible");
        embeddingProperties.setModel("text-embedding-3-small");
        embeddingProperties.setVectorDimensions(1536);

        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setVectorStore("pgvector");
        retrievalProperties.setDefaultTopK(5);

        questionAnsweringService = new QuestionAnsweringService(
                knowledgeBaseRepository,
                documentChunkRepository,
                embeddingProperties,
                retrievalProperties,
                openAiCompatibleClient
        );
    }

    @Test
    void getReadinessShouldRequireChunksBeforeWeek2CanProceed() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentChunkRepository.countByKnowledgeBaseId(100L)).thenReturn(0L);
        when(documentChunkRepository.countByKnowledgeBaseIdAndEmbeddingStatus(100L, EmbeddingStatus.EMBEDDED))
                .thenReturn(0L);

        QuestionAnsweringReadinessResponse response = questionAnsweringService.getReadiness("settlement-kb");

        assertThat(response.questionAnsweringReady()).isFalse();
        assertThat(response.indexedChunkCount()).isZero();
        assertThat(response.nextStep()).contains("Process at least one document");
    }

    @Test
    void getReadinessShouldReportReadyWhenEmbeddedChunksExist() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentChunkRepository.countByKnowledgeBaseId(100L)).thenReturn(12L);
        when(documentChunkRepository.countByKnowledgeBaseIdAndEmbeddingStatus(100L, EmbeddingStatus.EMBEDDED))
                .thenReturn(12L);

        QuestionAnsweringReadinessResponse response = questionAnsweringService.getReadiness("settlement-kb");

        assertThat(response.questionAnsweringReady()).isTrue();
        assertThat(response.embeddedChunkCount()).isEqualTo(12L);
        assertThat(response.vectorStore()).isEqualTo("pgvector");
        assertThat(response.nextStep()).contains("Proceed to Day 10 retrieval");
    }

    @Test
    void retrieveShouldReturnTopKChunksForQuestion() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(openAiCompatibleClient.createEmbedding(
                eq("http://localhost:8001/v1"),
                eq(""),
                eq("/embeddings"),
                eq("text-embedding-3-small"),
                eq("结算异常怎么处理")
        )).thenReturn(List.of(0.11D, 0.22D));
        when(documentChunkRepository.findTopKSimilarChunks(
                eq(100L),
                eq("[0.110000000000,0.220000000000]"),
                eq(3)
        )).thenReturn(List.of(createRetrievedChunkCandidate()));

        QuestionRetrievalResponse response = questionAnsweringService.retrieve(
                "settlement-kb",
                "结算异常怎么处理",
                3
        );

        assertThat(response.knowledgeBaseCode()).isEqualTo("settlement-kb");
        assertThat(response.topK()).isEqualTo(3);
        assertThat(response.hitCount()).isEqualTo(1);
        assertThat(response.chunks()).hasSize(1);
        assertThat(response.chunks().get(0).documentCode()).isEqualTo("DOC-1");
        assertThat(response.chunks().get(0).score()).isEqualTo(0.91D);
    }

    @Test
    void retrieveShouldRejectTopKAboveConfiguredMaximum() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));

        assertThatThrownBy(() -> questionAnsweringService.retrieve("settlement-kb", "问题", 11))
                .hasMessageContaining("topK must be <= 10");
    }

    private RetrievedChunkCandidate createRetrievedChunkCandidate() {
        RetrievedChunkCandidate candidate = new RetrievedChunkCandidate();
        candidate.setId(1L);
        candidate.setDocumentId(200L);
        candidate.setDocumentCode("DOC-1");
        candidate.setDocumentName("结算手册");
        candidate.setChunkIndex(0);
        candidate.setChunkType("TEXT");
        candidate.setContent("第一段内容");
        candidate.setStartOffset(0);
        candidate.setEndOffset(120);
        candidate.setEmbeddingModel("text-embedding-3-small");
        candidate.setScore(0.91D);
        return candidate;
    }
}
