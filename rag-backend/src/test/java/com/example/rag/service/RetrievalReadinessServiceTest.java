package com.example.rag.service;

import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.EmbeddingRebuildRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.EmbeddingConfigurationStateEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalReadinessServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private EmbeddingConfigurationStateService embeddingConfigurationStateService;

    @Mock
    private EmbeddingRebuildRunRepository embeddingRebuildRunRepository;

    @Test
    void getReadinessShouldBlockWhenReembedIsRequired() {
        RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
        embeddingProperties.setProvider("aliyun");
        embeddingProperties.setModel("text-embedding-v4");
        embeddingProperties.setVectorDimensions(1024);

        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setVectorStore("pgvector");
        retrievalProperties.setDefaultTopK(5);

        RetrievalReadinessService service = new RetrievalReadinessService(
                knowledgeBaseRepository,
                documentChunkRepository,
                embeddingConfigurationStateService,
                embeddingRebuildRunRepository,
                embeddingProperties,
                retrievalProperties
        );

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(10L);
        knowledgeBase.setKbCode("kb-1");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        EmbeddingConfigurationStateEntity state = new EmbeddingConfigurationStateEntity();
        state.setCurrentConfigFingerprint("new");
        state.setActiveConfigFingerprint("old");
        state.setActiveEmbeddingModel("text-embedding-3-small");
        state.setReembedRequired(true);

        when(knowledgeBaseRepository.findByCode("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(documentChunkRepository.countAvailableIndexedChunks(10L)).thenReturn(10L);
        when(documentChunkRepository.countAvailableEmbeddedChunks(10L)).thenReturn(10L);
        when(documentChunkRepository.countEmbeddedChunksWithDifferentDimensions(10L, 1024)).thenReturn(0L);
        when(embeddingConfigurationStateService.getRequiredState()).thenReturn(state);

        assertThat(service.getReadiness("kb-1").questionAnsweringReady()).isFalse();
        assertThat(service.getReadiness("kb-1").nextStep()).contains("Confirm and run rebuild");
        assertThatThrownBy(() -> service.assertRetrievalReady("kb-1"))
                .hasMessageContaining("Confirm and run rebuild");
    }

    @Test
    void getReadinessShouldExposeDimensionMismatchForLegacyVectors() {
        RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
        embeddingProperties.setProvider("aliyun");
        embeddingProperties.setModel("text-embedding-v4");
        embeddingProperties.setVectorDimensions(1024);

        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setVectorStore("pgvector");
        retrievalProperties.setDefaultTopK(5);

        RetrievalReadinessService service = new RetrievalReadinessService(
                knowledgeBaseRepository,
                documentChunkRepository,
                embeddingConfigurationStateService,
                embeddingRebuildRunRepository,
                embeddingProperties,
                retrievalProperties
        );

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(10L);
        knowledgeBase.setKbCode("kb-legacy");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        EmbeddingConfigurationStateEntity state = new EmbeddingConfigurationStateEntity();
        state.setCurrentConfigFingerprint("current");
        state.setActiveConfigFingerprint("current");
        state.setActiveEmbeddingModel("text-embedding-v4");
        state.setReembedRequired(false);

        when(knowledgeBaseRepository.findByCode("kb-legacy")).thenReturn(Optional.of(knowledgeBase));
        when(documentChunkRepository.countAvailableIndexedChunks(10L)).thenReturn(8L);
        when(documentChunkRepository.countAvailableEmbeddedChunks(10L)).thenReturn(8L);
        when(documentChunkRepository.countEmbeddedChunksWithDifferentDimensions(10L, 1024)).thenReturn(8L);
        when(embeddingConfigurationStateService.getRequiredState()).thenReturn(state);

        assertThat(service.getReadiness("kb-legacy").questionAnsweringReady()).isFalse();
        assertThat(service.getReadiness("kb-legacy").nextStep())
                .contains("dimensions do not match");
    }
}
