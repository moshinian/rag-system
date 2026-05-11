package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.model.enums.EmbeddingRebuildRunStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.EmbeddingRebuildRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.EmbeddingConfigurationStateEntity;
import com.example.rag.persistence.entity.EmbeddingRebuildRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一管理检索可用性判断。
 */
@Service
public class RetrievalReadinessService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingConfigurationStateService embeddingConfigurationStateService;
    private final EmbeddingRebuildRunRepository embeddingRebuildRunRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final RagRetrievalProperties ragRetrievalProperties;

    public RetrievalReadinessService(KnowledgeBaseRepository knowledgeBaseRepository,
                                     DocumentChunkRepository documentChunkRepository,
                                     EmbeddingConfigurationStateService embeddingConfigurationStateService,
                                     EmbeddingRebuildRunRepository embeddingRebuildRunRepository,
                                     RagEmbeddingProperties ragEmbeddingProperties,
                                     RagRetrievalProperties ragRetrievalProperties) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingConfigurationStateService = embeddingConfigurationStateService;
        this.embeddingRebuildRunRepository = embeddingRebuildRunRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.ragRetrievalProperties = ragRetrievalProperties;
    }

    @Transactional(readOnly = true)
    public QuestionAnsweringReadinessResponse getReadiness(String kbCode) {
        KnowledgeBaseEntity knowledgeBase = getKnowledgeBase(kbCode);
        ReadinessSnapshot snapshot = evaluate(knowledgeBase);
        EmbeddingConfigurationStateEntity state = snapshot.state();
        return new QuestionAnsweringReadinessResponse(
                knowledgeBase.getKbCode(),
                knowledgeBase.getStatus().name(),
                snapshot.ready(),
                ragEmbeddingProperties.getProvider(),
                ragEmbeddingProperties.getModel(),
                state.getActiveEmbeddingModel(),
                ragEmbeddingProperties.getVectorDimensions(),
                ragRetrievalProperties.getVectorStore(),
                ragRetrievalProperties.getDefaultTopK(),
                snapshot.indexedChunkCount(),
                snapshot.embeddedChunkCount(),
                Boolean.TRUE.equals(state.getReembedRequired()),
                snapshot.reembedInProgress(),
                state.getRebuildRunId(),
                snapshot.nextStep()
        );
    }

    @Transactional(readOnly = true)
    public void assertRetrievalReady(String kbCode) {
        KnowledgeBaseEntity knowledgeBase = getKnowledgeBase(kbCode);
        ReadinessSnapshot snapshot = evaluate(knowledgeBase);
        if (!snapshot.ready()) {
            throw new BusinessException(snapshot.nextStep());
        }
    }

    private ReadinessSnapshot evaluate(KnowledgeBaseEntity knowledgeBase) {
        long indexedChunkCount = documentChunkRepository.countAvailableIndexedChunks(knowledgeBase.getId());
        long embeddedChunkCount = documentChunkRepository.countAvailableEmbeddedChunks(knowledgeBase.getId());
        int expectedDimensions = ragEmbeddingProperties.getVectorDimensions() == null ? 0 : ragEmbeddingProperties.getVectorDimensions();
        long mismatchedDimensions = expectedDimensions <= 0
                ? 0
                : documentChunkRepository.countEmbeddedChunksWithDifferentDimensions(knowledgeBase.getId(), expectedDimensions);
        EmbeddingConfigurationStateEntity state = embeddingConfigurationStateService.getRequiredState();
        EmbeddingRebuildRunEntity rebuildRun = state.getRebuildRunId() == null
                ? null
                : embeddingRebuildRunRepository.findById(state.getRebuildRunId()).orElse(null);
        boolean knowledgeBaseActive = knowledgeBase.getStatus() == KnowledgeBaseStatus.ACTIVE;
        boolean reembedInProgress = rebuildRun != null
                && (rebuildRun.getStatus() == EmbeddingRebuildRunStatus.QUEUED
                || rebuildRun.getStatus() == EmbeddingRebuildRunStatus.RUNNING
                || rebuildRun.getStatus() == EmbeddingRebuildRunStatus.CANCELLING);
        String nextStep = resolveNextStep(
                knowledgeBaseActive,
                indexedChunkCount,
                embeddedChunkCount,
                mismatchedDimensions,
                Boolean.TRUE.equals(state.getReembedRequired()),
                reembedInProgress,
                rebuildRun
        );
        boolean ready = nextStep.equals("Retrieval prerequisites are ready.");
        return new ReadinessSnapshot(indexedChunkCount, embeddedChunkCount, reembedInProgress, ready, nextStep, state);
    }

    private String resolveNextStep(boolean knowledgeBaseActive,
                                   long indexedChunkCount,
                                   long embeddedChunkCount,
                                   long mismatchedDimensions,
                                   boolean reembedRequired,
                                   boolean reembedInProgress,
                                   EmbeddingRebuildRunEntity rebuildRun) {
        if (!knowledgeBaseActive) {
            return "Activate the knowledge base before running retrieval.";
        }
        if (reembedInProgress) {
            return rebuildRun != null && rebuildRun.getStatus() == EmbeddingRebuildRunStatus.CANCELLING
                    ? "Embedding rebuild is cancelling. Wait until it finishes before running retrieval."
                    : "Embedding rebuild is in progress. Wait until it finishes before running retrieval.";
        }
        if (rebuildRun != null && rebuildRun.getStatus() == EmbeddingRebuildRunStatus.CANCELLED) {
            return "Embedding rebuild was cancelled. Submit a new rebuild before running retrieval.";
        }
        if (reembedRequired) {
            return "Embedding configuration changed. Confirm and run rebuild before running retrieval.";
        }
        if (indexedChunkCount <= 0) {
            return "Process at least one document into chunks before running retrieval.";
        }
        if (embeddedChunkCount <= 0) {
            return "Generate embeddings for existing chunks before running retrieval.";
        }
        if (mismatchedDimensions > 0) {
            return "Embedded vector dimensions do not match the configured embedding dimensions.";
        }
        return "Retrieval prerequisites are ready.";
    }

    private KnowledgeBaseEntity getKnowledgeBase(String kbCode) {
        return knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
    }

    private record ReadinessSnapshot(long indexedChunkCount,
                                     long embeddedChunkCount,
                                     boolean reembedInProgress,
                                     boolean ready,
                                     String nextStep,
                                     EmbeddingConfigurationStateEntity state) {
    }
}
