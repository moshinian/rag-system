package com.example.rag.service;

import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAnsweringServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

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
                retrievalProperties
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
}
