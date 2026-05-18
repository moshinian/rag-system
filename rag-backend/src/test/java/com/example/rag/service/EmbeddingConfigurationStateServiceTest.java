package com.example.rag.service;

import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.EmbeddingConfigurationStateRepository;
import com.example.rag.persistence.entity.EmbeddingConfigurationStateEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingConfigurationStateServiceTest {

    @Mock
    private EmbeddingConfigurationStateRepository repository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Test
    void syncCurrentConfigurationShouldMarkLegacyEmbeddingsForRebuildOnFirstBoot() {
        RagEmbeddingProperties properties = createProperties();
        EmbeddingConfigurationStateService service = new EmbeddingConfigurationStateService(
                properties,
                repository,
                documentChunkRepository
        );

        when(repository.getSingleton()).thenReturn(Optional.empty());
        when(documentChunkRepository.existsEmbeddedChunksNeedingRebuild(service.calculateCurrentFingerprint(), 1024)).thenReturn(true);
        when(documentChunkRepository.findLatestEmbeddedModelInActiveKnowledgeBases()).thenReturn(Optional.of("bge-small-zh-v1.5"));

        service.syncCurrentConfiguration();

        ArgumentCaptor<EmbeddingConfigurationStateEntity> captor = ArgumentCaptor.forClass(EmbeddingConfigurationStateEntity.class);
        verify(repository).upsert(captor.capture());

        EmbeddingConfigurationStateEntity saved = captor.getValue();
        assertThat(saved.getCurrentConfigFingerprint()).isEqualTo(service.calculateCurrentFingerprint());
        assertThat(saved.getActiveConfigFingerprint()).isEqualTo("legacy-active-embeddings");
        assertThat(saved.getActiveEmbeddingModel()).isEqualTo("bge-small-zh-v1.5");
        assertThat(saved.getReembedRequired()).isTrue();
    }

    @Test
    void syncCurrentConfigurationShouldRepairIncorrectInitializedStateWhenLegacyEmbeddingsRemain() {
        RagEmbeddingProperties properties = createProperties();
        EmbeddingConfigurationStateService service = new EmbeddingConfigurationStateService(
                properties,
                repository,
                documentChunkRepository
        );
        String currentFingerprint = service.calculateCurrentFingerprint();

        EmbeddingConfigurationStateEntity existing = new EmbeddingConfigurationStateEntity();
        existing.setCurrentConfigFingerprint(currentFingerprint);
        existing.setActiveConfigFingerprint(currentFingerprint);
        existing.setActiveEmbeddingModel("text-embedding-v4");
        existing.setReembedRequired(false);

        when(repository.getSingleton()).thenReturn(Optional.of(existing));
        when(documentChunkRepository.existsEmbeddedChunksNeedingRebuild(currentFingerprint, 1024)).thenReturn(true);
        when(documentChunkRepository.findLatestEmbeddedModelInActiveKnowledgeBases()).thenReturn(Optional.of("bge-small-zh-v1.5"));

        service.syncCurrentConfiguration();

        ArgumentCaptor<EmbeddingConfigurationStateEntity> captor = ArgumentCaptor.forClass(EmbeddingConfigurationStateEntity.class);
        verify(repository).upsert(captor.capture());

        EmbeddingConfigurationStateEntity saved = captor.getValue();
        assertThat(saved.getActiveConfigFingerprint()).isEqualTo("legacy-active-embeddings");
        assertThat(saved.getActiveEmbeddingModel()).isEqualTo("bge-small-zh-v1.5");
        assertThat(saved.getReembedRequired()).isTrue();
    }

    private RagEmbeddingProperties createProperties() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setProvider("rag-ai-service");
        properties.setModel("text-embedding-v4");
        properties.setDistanceMetric("cosine");
        properties.setVectorDimensions(1024);
        return properties;
    }
}
