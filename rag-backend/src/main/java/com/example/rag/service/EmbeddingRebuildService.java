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

    /** 注入全量重嵌入任务所需依赖。 */
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

    /** 提交一次全量重嵌入任务。 */
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
        // 全量重嵌入要求索引链路静止，避免 process/embed 和 rebuild 同时改写 chunk 向量状态。
        if (indexingTaskRepository.existsAnyActiveTask(TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("Document indexing is running. Wait until it finishes before rebuilding embeddings.");
        }
        String currentFingerprint = embeddingConfigurationStateService.calculateCurrentFingerprint();
        // 当前配置与活动配置完全一致时，不需要重复跑一次重嵌入。
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

        // 先把旧 embedding 标记为待重建，再启动后台任务，保证 readiness 会立刻反映不可检索状态。
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
            /** 在事务提交后启动异步执行。 */
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

    /** 恢复异常遗留的重嵌入任务。 */
    @Scheduled(
            fixedDelayString = "${rag.indexing.recovery.scan-interval-ms:60000}",
            initialDelayString = "${rag.indexing.recovery.initial-delay-ms:30000}"
    )
    public void recoverQueuedRebuildRuns() {
        List<EmbeddingRebuildRunEntity> queuedRuns = embeddingRebuildRunRepository.findByStatuses(
                List.of(EmbeddingRebuildRunStatus.QUEUED),
                RECOVERY_SCAN_LIMIT
        );
        // 这里只接管仍停留在 QUEUED 的任务，RUNNING 任务由其自身心跳负责。
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

    /** 异步执行重嵌入任务。 */
    private void runAsync(Long runId) {
        EmbeddingRebuildRunEntity run = embeddingRebuildRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException("Embedding rebuild run not found: " + runId));
        if (run.getStatus() != EmbeddingRebuildRunStatus.QUEUED) {
            // 重复投递或恢复扫描并发命中时，只允许第一条真正消费任务。
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
                // 重嵌入以“当前仍启用且已索引”的文档集合为准，不强行回放提交时的快照。
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

    /** 按知识库 ID 解析知识库编码。 */
    private String resolveKnowledgeBaseCode(Long knowledgeBaseId) {
        return knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException("Knowledge base not found for rebuild: " + knowledgeBaseId))
                .getKbCode();
    }

    /** 归一化操作人。 */
    private String normalizeOperator(String operator) {
        return operator == null || operator.trim().isBlank() ? "system" : operator.trim();
    }

    /** 截断过长错误消息。 */
    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown rebuild error";
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    /** 在投递失败后把重嵌入任务标记为失败。 */
    private void markRunDispatchFailed(Long runId, RuntimeException ex) {
        embeddingRebuildRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(EmbeddingRebuildRunStatus.FAILED);
            run.setErrorSummary(truncate("Failed to dispatch embedding rebuild: " + ex.getMessage()));
            OffsetDateTime now = OffsetDateTime.now();
            run.setFinishedAt(now);
            run.setLastHeartbeatAt(now);
            // 任务连执行器都没进去时，直接把总量记为失败，便于前端和运维快速判断规模。
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

    /** 清理重嵌入相关缓存。 */
    private void evictRebuildCaches() {
        // rebuild 会同时影响 readiness、检索结果和 chunk 详情，直接按 cache name 全量清空更安全。
        evictAll(CacheNames.QA_READINESS);
        evictAll(CacheNames.QA_RETRIEVAL);
        evictAll(CacheNames.DOCUMENT_CHUNKS);
    }

    /** 清理指定缓存的全部条目。 */
    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(Objects.requireNonNull(cacheName, "cacheName must not be null"));
        if (cache != null) {
            cache.clear();
        }
    }
}
