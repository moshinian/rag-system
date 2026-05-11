package com.example.rag.service;

import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.EmbeddingConfigurationStateRepository;
import com.example.rag.persistence.entity.EmbeddingConfigurationStateEntity;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 管理 embedding 配置 fingerprint 与重嵌入状态。
 */
@Service
public class EmbeddingConfigurationStateService {
    private static final String LEGACY_ACTIVE_FINGERPRINT = "legacy-active-embeddings";

    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final EmbeddingConfigurationStateRepository repository;
    private final DocumentChunkRepository documentChunkRepository;

    public EmbeddingConfigurationStateService(RagEmbeddingProperties ragEmbeddingProperties,
                                              EmbeddingConfigurationStateRepository repository,
                                              DocumentChunkRepository documentChunkRepository) {
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.repository = repository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @PostConstruct
    @Transactional
    public void syncCurrentConfiguration() {
        String currentFingerprint = calculateCurrentFingerprint();
        boolean legacyEmbeddingsDetected = detectLegacyActiveEmbeddings(currentFingerprint);
        EmbeddingConfigurationStateEntity state = repository.getSingleton().orElseGet(() -> {
            EmbeddingConfigurationStateEntity initial = new EmbeddingConfigurationStateEntity();
            initial.setCurrentConfigFingerprint(currentFingerprint);
            initial.setActiveConfigFingerprint(legacyEmbeddingsDetected ? LEGACY_ACTIVE_FINGERPRINT : currentFingerprint);
            initial.setActiveEmbeddingModel(resolveActiveEmbeddingModel(legacyEmbeddingsDetected));
            initial.setReembedRequired(legacyEmbeddingsDetected);
            return initial;
        });
        if (state.getCurrentConfigFingerprint() == null) {
            state.setCurrentConfigFingerprint(currentFingerprint);
        }
        if (state.getActiveConfigFingerprint() == null) {
            state.setActiveConfigFingerprint(legacyEmbeddingsDetected ? LEGACY_ACTIVE_FINGERPRINT : currentFingerprint);
        }
        if (state.getActiveEmbeddingModel() == null || state.getActiveEmbeddingModel().isBlank()) {
            state.setActiveEmbeddingModel(resolveActiveEmbeddingModel(legacyEmbeddingsDetected));
        }
        if (state.getReembedRequired() == null) {
            state.setReembedRequired(false);
        }
        if (!Objects.equals(state.getCurrentConfigFingerprint(), currentFingerprint)) {
            state.setCurrentConfigFingerprint(currentFingerprint);
            state.setReembedRequired(true);
            state.setReembedFinishedAt(null);
        }
        if (legacyEmbeddingsDetected
                && Objects.equals(state.getCurrentConfigFingerprint(), currentFingerprint)
                && !Boolean.TRUE.equals(state.getReembedRequired())
                && Objects.equals(state.getActiveConfigFingerprint(), currentFingerprint)) {
            state.setActiveConfigFingerprint(LEGACY_ACTIVE_FINGERPRINT);
            state.setActiveEmbeddingModel(resolveActiveEmbeddingModel(true));
            state.setReembedRequired(true);
            state.setReembedFinishedAt(null);
        }
        repository.upsert(state);
    }

    @Transactional(readOnly = true)
    public EmbeddingConfigurationStateEntity getRequiredState() {
        return repository.getSingleton().orElseThrow(() -> new IllegalStateException("Embedding configuration state is not initialized"));
    }

    @Transactional(readOnly = true)
    public String calculateCurrentFingerprint() {
        String raw = String.join("|",
                normalize(ragEmbeddingProperties.getProvider()),
                normalize(ragEmbeddingProperties.getBaseUrl()),
                normalize(ragEmbeddingProperties.getModel()),
                normalize(ragEmbeddingProperties.getEmbeddingPath()),
                normalize(ragEmbeddingProperties.getDistanceMetric()),
                String.valueOf(ragEmbeddingProperties.getVectorDimensions() == null ? 0 : ragEmbeddingProperties.getVectorDimensions())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    @Transactional
    public EmbeddingConfigurationStateEntity markRebuildSubmitted(Long rebuildRunId, String operator) {
        EmbeddingConfigurationStateEntity state = getRequiredState();
        OffsetDateTime now = OffsetDateTime.now();
        state.setReembedRequired(true);
        state.setRebuildRunId(rebuildRunId);
        state.setReembedConfirmedBy(operator);
        state.setReembedConfirmedAt(now);
        state.setReembedStartedAt(now);
        state.setReembedFinishedAt(null);
        repository.upsert(state);
        return state;
    }

    @Transactional
    public void markRebuildCompleted() {
        EmbeddingConfigurationStateEntity state = getRequiredState();
        OffsetDateTime now = OffsetDateTime.now();
        state.setActiveConfigFingerprint(state.getCurrentConfigFingerprint());
        state.setActiveEmbeddingModel(ragEmbeddingProperties.getModel());
        state.setReembedRequired(false);
        state.setRebuildRunId(null);
        state.setReembedFinishedAt(now);
        repository.upsert(state);
    }

    @Transactional
    public void markRebuildFailed(Long rebuildRunId) {
        EmbeddingConfigurationStateEntity state = getRequiredState();
        state.setReembedRequired(true);
        state.setRebuildRunId(rebuildRunId);
        state.setReembedFinishedAt(OffsetDateTime.now());
        repository.upsert(state);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean detectLegacyActiveEmbeddings(String currentFingerprint) {
        int expectedDimensions = ragEmbeddingProperties.getVectorDimensions() == null ? 0 : ragEmbeddingProperties.getVectorDimensions();
        if (expectedDimensions <= 0) {
            return false;
        }
        return documentChunkRepository.existsEmbeddedChunksNeedingRebuild(currentFingerprint, expectedDimensions);
    }

    private String resolveActiveEmbeddingModel(boolean legacyEmbeddingsDetected) {
        if (!legacyEmbeddingsDetected) {
            return ragEmbeddingProperties.getModel();
        }
        return documentChunkRepository.findLatestEmbeddedModelInActiveKnowledgeBases()
                .filter(model -> !model.isBlank())
                .orElse(ragEmbeddingProperties.getModel());
    }
}
