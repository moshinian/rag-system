package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.CacheNames;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.model.enums.EmbeddingRebuildRunStatus;
import com.example.rag.model.response.EmbeddingRebuildSubmitResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.EmbeddingRebuildRunRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.EmbeddingRebuildRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 全量重嵌入后台任务服务。
 */
@Service
public class EmbeddingRebuildService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRebuildService.class);
    private static final String TASK_TYPE_DOCUMENT_INDEXING = "DOCUMENT_INDEXING";
    private static final int RECOVERY_SCAN_LIMIT = 10;

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final EmbeddingConfigurationStateService embeddingConfigurationStateService;
    private final EmbeddingRebuildRunRepository embeddingRebuildRunRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final IndexingTaskRepository indexingTaskRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final Executor indexingExecutor;
    private final CacheManager cacheManager;

    public EmbeddingRebuildService(SnowflakeIdGenerator snowflakeIdGenerator,
                                   RagEmbeddingProperties ragEmbeddingProperties,
                                   EmbeddingConfigurationStateService embeddingConfigurationStateService,
                                   EmbeddingRebuildRunRepository embeddingRebuildRunRepository,
                                   DocumentChunkRepository documentChunkRepository,
                                   DocumentRepository documentRepository,
                                   DocumentEmbeddingService documentEmbeddingService,
                                   IndexingTaskRepository indexingTaskRepository,
                                   KnowledgeBaseRepository knowledgeBaseRepository,
                                   @Qualifier("indexingExecutor") Executor indexingExecutor,
                                   CacheManager cacheManager) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.embeddingConfigurationStateService = embeddingConfigurationStateService;
        this.embeddingRebuildRunRepository = embeddingRebuildRunRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentRepository = documentRepository;
        this.documentEmbeddingService = documentEmbeddingService;
        this.indexingTaskRepository = indexingTaskRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.indexingExecutor = indexingExecutor;
        this.cacheManager = cacheManager;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.QA_READINESS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DOCUMENT_CHUNKS, allEntries = true)
    })
    public EmbeddingRebuildSubmitResponse submit(String operator) {
        String normalizedOperator = normalizeOperator(operator);
        if (embeddingRebuildRunRepository.existsActiveRun()) {
            throw new BusinessException("An embedding rebuild run is already active");
        }
        if (indexingTaskRepository.existsAnyActiveTask(TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("Document indexing is running. Wait until it finishes before rebuilding embeddings.");
        }
        String currentFingerprint = embeddingConfigurationStateService.calculateCurrentFingerprint();
        if (currentFingerprint.equals(embeddingConfigurationStateService.getRequiredState().getActiveConfigFingerprint())) {
            throw new BusinessException("Embedding configuration has not changed");
        }

        List<DocumentEntity> documents = documentRepository.findIndexedDocumentsInActiveKnowledgeBases();
        EmbeddingRebuildRunEntity run = new EmbeddingRebuildRunEntity();
        OffsetDateTime now = OffsetDateTime.now();
        run.setId(snowflakeIdGenerator.nextId());
        run.setStatus(EmbeddingRebuildRunStatus.QUEUED);
        run.setTargetFingerprint(currentFingerprint);
        run.setTargetModel(ragEmbeddingProperties.getModel());
        run.setTargetProvider(ragEmbeddingProperties.getProvider());
        run.setVectorDimensions(ragEmbeddingProperties.getVectorDimensions());
        run.setDistanceMetric(ragEmbeddingProperties.getDistanceMetric());
        run.setCreatedBy(normalizedOperator);
        run.setStartedAt(now);
        run.setLastHeartbeatAt(now);
        run.setTotalDocumentCount(documents.size());
        run.setSucceededDocumentCount(0);
        run.setFailedDocumentCount(0);
        embeddingRebuildRunRepository.insert(run);

        documentChunkRepository.resetEmbeddingsForRebuild(
                ragEmbeddingProperties.getModel(),
                ragEmbeddingProperties.getProvider(),
                currentFingerprint,
                run.getId(),
                normalizedOperator,
                now
        );
        embeddingConfigurationStateService.markRebuildSubmitted(run.getId(), normalizedOperator);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchRun(run.getId());
            }
        });

        return new EmbeddingRebuildSubmitResponse(
                run.getId(),
                run.getStatus().name(),
                run.getTargetFingerprint(),
                run.getTargetModel(),
                run.getTargetProvider(),
                run.getVectorDimensions(),
                run.getDistanceMetric(),
                normalizedOperator,
                now
        );
    }

    @Scheduled(
            fixedDelayString = "${rag.indexing.recovery.scan-interval-ms:60000}",
            initialDelayString = "${rag.indexing.recovery.initial-delay-ms:30000}"
    )
    public void recoverQueuedRebuildRuns() {
        List<EmbeddingRebuildRunEntity> queuedRuns = embeddingRebuildRunRepository.findByStatuses(
                List.of(EmbeddingRebuildRunStatus.QUEUED),
                RECOVERY_SCAN_LIMIT
        );
        for (EmbeddingRebuildRunEntity queuedRun : queuedRuns) {
            dispatchRun(queuedRun.getId());
        }
    }

    void dispatchRun(Long runId) {
        try {
            indexingExecutor.execute(() -> runAsync(runId));
        } catch (RejectedExecutionException ex) {
            markRunDispatchFailed(runId, ex);
        }
    }

    private void runAsync(Long runId) {
        EmbeddingRebuildRunEntity run = embeddingRebuildRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException("Embedding rebuild run not found: " + runId));
        if (run.getStatus() != EmbeddingRebuildRunStatus.QUEUED) {
            return;
        }
        run.setStatus(EmbeddingRebuildRunStatus.RUNNING);
        run.setLastHeartbeatAt(OffsetDateTime.now());
        embeddingRebuildRunRepository.updateById(run);
        int succeeded = 0;
        int failed = 0;
        String errorSummary = null;
        try {
            List<DocumentEntity> documents = documentRepository.findIndexedDocumentsInActiveKnowledgeBases();
            for (DocumentEntity document : documents) {
                run.setLastHeartbeatAt(OffsetDateTime.now());
                embeddingRebuildRunRepository.updateById(run);
                documentEmbeddingService.embedForRebuild(
                        resolveKnowledgeBaseCode(document.getKnowledgeBaseId()),
                        document.getDocumentCode(),
                        run.getCreatedBy(),
                        run.getId()
                );
                succeeded++;
                run.setSucceededDocumentCount(succeeded);
                embeddingRebuildRunRepository.updateById(run);
            }
            run.setStatus(EmbeddingRebuildRunStatus.SUCCEEDED);
            run.setFinishedAt(OffsetDateTime.now());
            run.setLastHeartbeatAt(run.getFinishedAt());
            run.setFailedDocumentCount(failed);
            embeddingRebuildRunRepository.updateById(run);
            embeddingConfigurationStateService.markRebuildCompleted();
            evictRebuildCaches();
            log.info(StructuredLogMessage.of("embedding.rebuild.succeeded")
                    .field("runId", run.getId())
                    .field("documentCount", succeeded)
                    .build());
        } catch (RuntimeException ex) {
            failed++;
            errorSummary = truncate(ex.getMessage());
            run.setStatus(EmbeddingRebuildRunStatus.FAILED);
            run.setFailedDocumentCount(failed);
            run.setErrorSummary(errorSummary);
            run.setFinishedAt(OffsetDateTime.now());
            run.setLastHeartbeatAt(run.getFinishedAt());
            embeddingRebuildRunRepository.updateById(run);
            embeddingConfigurationStateService.markRebuildFailed(run.getId());
            evictRebuildCaches();
            log.warn(StructuredLogMessage.of("embedding.rebuild.failed")
                    .field("runId", run.getId())
                    .field("message", errorSummary)
                    .build());
        }
    }

    private String resolveKnowledgeBaseCode(Long knowledgeBaseId) {
        return knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException("Knowledge base not found for rebuild: " + knowledgeBaseId))
                .getKbCode();
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.trim().isBlank() ? "system" : operator.trim();
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown rebuild error";
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private void markRunDispatchFailed(Long runId, RuntimeException ex) {
        embeddingRebuildRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(EmbeddingRebuildRunStatus.FAILED);
            run.setErrorSummary(truncate("Failed to dispatch embedding rebuild: " + ex.getMessage()));
            OffsetDateTime now = OffsetDateTime.now();
            run.setFinishedAt(now);
            run.setLastHeartbeatAt(now);
            run.setFailedDocumentCount(run.getTotalDocumentCount());
            embeddingRebuildRunRepository.updateById(run);
            embeddingConfigurationStateService.markRebuildFailed(runId);
            evictRebuildCaches();
            log.warn(StructuredLogMessage.of("embedding.rebuild.dispatch_failed")
                    .field("runId", runId)
                    .field("message", ex.getMessage())
                    .build());
        });
    }

    private void evictRebuildCaches() {
        evictAll(CacheNames.QA_READINESS);
        evictAll(CacheNames.QA_RETRIEVAL);
        evictAll(CacheNames.DOCUMENT_CHUNKS);
    }

    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(Objects.requireNonNull(cacheName, "cacheName must not be null"));
        if (cache != null) {
            cache.clear();
        }
    }
}
