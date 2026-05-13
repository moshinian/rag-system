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

    /** 注入 embedding 配置状态管理所需依赖。 */
    public EmbeddingConfigurationStateService(RagEmbeddingProperties ragEmbeddingProperties,
                                              EmbeddingConfigurationStateRepository repository,
                                              DocumentChunkRepository documentChunkRepository) {
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.repository = repository;
        this.documentChunkRepository = documentChunkRepository;
    }

    /** 同步当前 embedding 配置快照。 */
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
            // 启动配置变化只更新 current，不直接覆盖 active，避免把旧向量误标为可安全检索。
            state.setCurrentConfigFingerprint(currentFingerprint);
            state.setReembedRequired(true);
            state.setReembedFinishedAt(null);
        }
        if (legacyEmbeddingsDetected
                && Objects.equals(state.getCurrentConfigFingerprint(), currentFingerprint)
                && !Boolean.TRUE.equals(state.getReembedRequired())
                && Objects.equals(state.getActiveConfigFingerprint(), currentFingerprint)) {
            // 这里处理“配置没变但库里仍有旧向量”的历史补偿场景，强制重新进入重嵌入流程。
            state.setActiveConfigFingerprint(LEGACY_ACTIVE_FINGERPRINT);
            state.setActiveEmbeddingModel(resolveActiveEmbeddingModel(true));
            state.setReembedRequired(true);
            state.setReembedFinishedAt(null);
        }
        repository.upsert(state);
    }

    /** 读取当前必需的 embedding 配置状态。 */
    @Transactional(readOnly = true)
    public EmbeddingConfigurationStateEntity getRequiredState() {
        return repository.getSingleton().orElseThrow(() -> new IllegalStateException("Embedding configuration state is not initialized"));
    }

    /** 计算当前 embedding 配置指纹。 */
    @Transactional(readOnly = true)
    public String calculateCurrentFingerprint() {
        // profile 指纹代表“当前向量契约”，任何参与检索兼容性的字段变化都必须进入摘要。
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

    /** 标记重嵌入任务已提交。 */
    @Transactional
    public EmbeddingConfigurationStateEntity markRebuildSubmitted(Long rebuildRunId, String operator) {
        EmbeddingConfigurationStateEntity state = getRequiredState();
        OffsetDateTime now = OffsetDateTime.now();
        // 一旦确认提交 rebuild，就立刻把系统视图切到“需要重建/重建中”。
        state.setReembedRequired(true);
        state.setRebuildRunId(rebuildRunId);
        state.setReembedConfirmedBy(operator);
        state.setReembedConfirmedAt(now);
        state.setReembedStartedAt(now);
        state.setReembedFinishedAt(null);
        repository.upsert(state);
        return state;
    }

    /** 标记重嵌入任务已完成。 */
    @Transactional
    public void markRebuildCompleted() {
        EmbeddingConfigurationStateEntity state = getRequiredState();
        OffsetDateTime now = OffsetDateTime.now();
        // 只有全量任务成功后，activeConfigFingerprint 才能追平 currentConfigFingerprint。
        state.setActiveConfigFingerprint(state.getCurrentConfigFingerprint());
        state.setActiveEmbeddingModel(ragEmbeddingProperties.getModel());
        state.setReembedRequired(false);
        state.setRebuildRunId(null);
        state.setReembedFinishedAt(now);
        repository.upsert(state);
    }

    /** 标记重嵌入任务已失败。 */
    @Transactional
    public void markRebuildFailed(Long rebuildRunId) {
        EmbeddingConfigurationStateEntity state = getRequiredState();
        state.setReembedRequired(true);
        state.setRebuildRunId(rebuildRunId);
        state.setReembedFinishedAt(OffsetDateTime.now());
        repository.upsert(state);
    }

    /** 归一化配置字符串，避免空值参与指纹计算时报错。 */
    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /** 判断库内是否仍存在需要重嵌入的历史向量数据。 */
    private boolean detectLegacyActiveEmbeddings(String currentFingerprint) {
        int expectedDimensions = ragEmbeddingProperties.getVectorDimensions() == null ? 0 : ragEmbeddingProperties.getVectorDimensions();
        if (expectedDimensions <= 0) {
            return false;
        }
        return documentChunkRepository.existsEmbeddedChunksNeedingRebuild(currentFingerprint, expectedDimensions);
    }

    /** 解析当前应展示为生效中的 embedding 模型名。 */
    private String resolveActiveEmbeddingModel(boolean legacyEmbeddingsDetected) {
        if (!legacyEmbeddingsDetected) {
            return ragEmbeddingProperties.getModel();
        }
        return documentChunkRepository.findLatestEmbeddedModelInActiveKnowledgeBases()
                .filter(model -> !model.isBlank())
                .orElse(ragEmbeddingProperties.getModel());
    }
}
