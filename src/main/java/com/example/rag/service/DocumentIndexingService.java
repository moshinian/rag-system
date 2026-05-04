package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagIndexingProperties;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.IndexingTaskStage;
import com.example.rag.model.enums.IndexingTaskStatus;
import com.example.rag.model.enums.IndexingTaskTriggerSource;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentEmbeddingResponse;
import com.example.rag.model.response.DocumentIndexingTaskResponse;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 文档异步索引服务。
 */
@Service
public class DocumentIndexingService {

    private static final String TASK_TYPE_DOCUMENT_INDEXING = "DOCUMENT_INDEXING";
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RagIndexingProperties ragIndexingProperties;
    private final Executor indexingExecutor;

    public DocumentIndexingService(KnowledgeBaseRepository knowledgeBaseRepository,
                                   DocumentRepository documentRepository,
                                   IndexingTaskRepository indexingTaskRepository,
                                   DocumentProcessingService documentProcessingService,
                                   DocumentEmbeddingService documentEmbeddingService,
                                   SnowflakeIdGenerator snowflakeIdGenerator,
                                   RagIndexingProperties ragIndexingProperties,
                                   @Qualifier("indexingExecutor") Executor indexingExecutor) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.indexingTaskRepository = indexingTaskRepository;
        this.documentProcessingService = documentProcessingService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.ragIndexingProperties = ragIndexingProperties;
        this.indexingExecutor = indexingExecutor;
    }

    /** 提交一条后台索引任务。 */
    public DocumentIndexingTaskResponse submit(String kbCode, String documentCode, String operator) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);

        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        if (document.getStatus() == DocumentStatus.DISABLED) {
            throw new BusinessException("Document is disabled and cannot be indexed: " + documentCode);
        }
        if (indexingTaskRepository.existsActiveTask(document.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("An active indexing task already exists for document: " + documentCode);
        }

        IndexingTaskEntity task = createTask(document, null, IndexingTaskTriggerSource.SUBMIT, normalizeOperator(operator));
        dispatch(task.getId());
        log.info(StructuredLogMessage.of("indexing.task.submitted")
                .field("taskId", task.getId())
                .field("kbCode", kbCode)
                .field("documentCode", documentCode)
                .field("triggerSource", task.getTriggerSource())
                .field("operator", task.getCreatedBy())
                .build());
        return toResponse(task, document, kbCode);
    }

    /** 查询文档的索引任务历史。 */
    public List<DocumentIndexingTaskResponse> listTasks(String kbCode, String documentCode) {
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        return indexingTaskRepository.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream()
                .map(task -> toResponse(task, document, kbCode))
                .toList();
    }

    /** 手动重试失败任务。 */
    public DocumentIndexingTaskResponse retry(String kbCode, String documentCode, Long taskId, String operator) {
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        IndexingTaskEntity task = indexingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Indexing task not found: " + taskId));
        if (!Objects.equals(task.getDocumentId(), document.getId())) {
            throw new BusinessException("Indexing task does not belong to document: " + taskId);
        }
        if (task.getStatus() != IndexingTaskStatus.FAILED) {
            throw new BusinessException("Only FAILED indexing tasks can be retried: " + taskId);
        }
        if (indexingTaskRepository.existsActiveTask(document.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("An active indexing task already exists for document: " + documentCode);
        }
        if (task.getRetryCount() != null && task.getMaxRetryCount() != null
                && task.getRetryCount() >= task.getMaxRetryCount()) {
            throw new BusinessException("Indexing task exceeded max retry count: " + taskId);
        }

        IndexingTaskEntity retryTask = createRetryTask(document, task, IndexingTaskTriggerSource.MANUAL_RETRY, normalizeOperator(operator));
        markRecovered(task, "Manually retried by task " + retryTask.getId());
        dispatch(retryTask.getId());
        log.info(StructuredLogMessage.of("indexing.task.retried")
                .field("taskId", retryTask.getId())
                .field("parentTaskId", task.getId())
                .field("kbCode", kbCode)
                .field("documentCode", documentCode)
                .field("retryCount", retryTask.getRetryCount())
                .field("operator", retryTask.getCreatedBy())
                .build());
        return toResponse(retryTask, document, kbCode);
    }

    /** 定时扫描卡住的队列中/运行中任务，并重新投递。 */
    @Scheduled(
            fixedDelayString = "${rag.indexing.recovery.scan-interval-ms:60000}",
            initialDelayString = "${rag.indexing.recovery.initial-delay-ms:30000}"
    )
    public void recoverStaleTasks() {
        if (!ragIndexingProperties.getRecovery().isEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now()
                .minusSeconds(Math.max(30, ragIndexingProperties.getRecovery().getStaleAfterSeconds()));
        int limit = Math.max(1, ragIndexingProperties.getRecovery().getScanLimit());
        List<IndexingTaskEntity> staleTasks = indexingTaskRepository.findRecoverableTasks(
                TASK_TYPE_DOCUMENT_INDEXING,
                cutoff,
                limit
        );
        if (!staleTasks.isEmpty()) {
            log.info(StructuredLogMessage.of("indexing.recovery.scan_found")
                    .field("taskCount", staleTasks.size())
                    .field("cutoff", cutoff)
                    .build());
        }
        for (IndexingTaskEntity staleTask : staleTasks) {
            try {
                recoverStaleTask(staleTask);
            } catch (RuntimeException ex) {
                // 恢复扫描不因为单条坏任务中断整轮调度。
                log.warn(StructuredLogMessage.of("indexing.recovery.scan_failed")
                        .field("taskId", staleTask.getId())
                        .field("documentId", staleTask.getDocumentId())
                        .field("message", ex.getMessage())
                        .build());
            }
        }
    }

    private void recoverStaleTask(IndexingTaskEntity staleTask) {
        if (indexingTaskRepository.existsOtherActiveTask(staleTask.getDocumentId(), TASK_TYPE_DOCUMENT_INDEXING, staleTask.getId())) {
            log.info(StructuredLogMessage.of("indexing.recovery.skipped")
                    .field("taskId", staleTask.getId())
                    .field("documentId", staleTask.getDocumentId())
                    .field("reason", "other_active_task_exists")
                    .build());
            return;
        }
        DocumentEntity document = documentRepository.findById(staleTask.getDocumentId())
                .orElseThrow(() -> new BusinessException("Document not found for indexing task: " + staleTask.getId()));
        if (staleTask.getRetryCount() != null && staleTask.getMaxRetryCount() != null
                && staleTask.getRetryCount() >= staleTask.getMaxRetryCount()) {
            staleTask.setStatus(IndexingTaskStatus.FAILED);
            staleTask.setErrorMessage(truncate("Task exceeded max retry count during recovery"));
            staleTask.setFinishedAt(OffsetDateTime.now());
            staleTask.setLastHeartbeatAt(OffsetDateTime.now());
            indexingTaskRepository.updateById(staleTask);
            log.warn(StructuredLogMessage.of("indexing.recovery.failed")
                    .field("taskId", staleTask.getId())
                    .field("documentId", staleTask.getDocumentId())
                    .field("reason", "max_retry_exceeded")
                    .build());
            return;
        }

        IndexingTaskEntity retryTask = createRetryTask(document, staleTask, IndexingTaskTriggerSource.RECOVERY, staleTask.getCreatedBy());
        markRecovered(staleTask, "Recovered by task " + retryTask.getId());
        dispatch(retryTask.getId());
        log.info(StructuredLogMessage.of("indexing.recovery.dispatched")
                .field("taskId", staleTask.getId())
                .field("recoveryTaskId", retryTask.getId())
                .field("documentId", staleTask.getDocumentId())
                .field("retryCount", retryTask.getRetryCount())
                .build());
    }

    private void runAsync(Long taskId) {
        IndexingTaskEntity task = indexingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Indexing task not found: " + taskId));
        DocumentEntity document = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new BusinessException("Document not found for indexing task: " + taskId));
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(task.getKnowledgeBaseId())
                .orElseThrow(() -> new BusinessException("Knowledge base not found for indexing task: " + taskId));
        String operator = task.getCreatedBy();
        MDC.put("taskId", String.valueOf(task.getId()));
        MDC.put("kbCode", knowledgeBase.getKbCode());
        MDC.put("documentCode", document.getDocumentCode());
        try {
            log.info(StructuredLogMessage.of("indexing.task.started")
                    .field("taskId", task.getId())
                    .field("kbCode", knowledgeBase.getKbCode())
                    .field("documentCode", document.getDocumentCode())
                    .field("triggerSource", task.getTriggerSource())
                    .field("retryCount", task.getRetryCount())
                    .build());
            task.setStatus(IndexingTaskStatus.RUNNING);
            task.setTaskStage(IndexingTaskStage.DOCUMENT_PROCESSING);
            task.setErrorMessage(null);
            touchHeartbeat(task, null);

            DocumentProcessResponse processResponse = documentProcessingService.process(
                    knowledgeBase.getKbCode(),
                    document.getDocumentCode(),
                    operator
            );
            task.setParserName(processResponse.parserName());
            task.setChunkCount(processResponse.chunkCount());
            task.setTaskStage(IndexingTaskStage.DOCUMENT_EMBEDDING);
            touchHeartbeat(task, null);

            DocumentEmbeddingResponse embeddingResponse = documentEmbeddingService.embed(
                    knowledgeBase.getKbCode(),
                    document.getDocumentCode()
            );
            task.setEmbeddedChunkCount(Math.toIntExact(embeddingResponse.totalEmbeddedChunkCount()));
            task.setStatus(IndexingTaskStatus.SUCCEEDED);
            task.setTaskStage(IndexingTaskStage.COMPLETED);
            task.setFinishedAt(OffsetDateTime.now());
            touchHeartbeat(task, null);
            log.info(StructuredLogMessage.of("indexing.task.succeeded")
                    .field("taskId", task.getId())
                    .field("kbCode", knowledgeBase.getKbCode())
                    .field("documentCode", document.getDocumentCode())
                    .field("chunkCount", task.getChunkCount())
                    .field("embeddedChunkCount", task.getEmbeddedChunkCount())
                    .build());
        } catch (RuntimeException ex) {
            task.setStatus(IndexingTaskStatus.FAILED);
            task.setFinishedAt(OffsetDateTime.now());
            touchHeartbeat(task, truncate(ex.getMessage()));
            log.warn(StructuredLogMessage.of("indexing.task.failed")
                    .field("taskId", task.getId())
                    .field("kbCode", knowledgeBase.getKbCode())
                    .field("documentCode", document.getDocumentCode())
                    .field("taskStage", task.getTaskStage())
                    .field("message", ex.getMessage())
                    .build());
        } finally {
            MDC.remove("documentCode");
            MDC.remove("kbCode");
            MDC.remove("taskId");
        }
    }

    private DocumentIndexingTaskResponse toResponse(IndexingTaskEntity task, DocumentEntity document, String kbCode) {
        return new DocumentIndexingTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus() == null ? IndexingTaskStatus.QUEUED.name() : task.getStatus().name(),
                task.getTaskStage() == null ? IndexingTaskStage.QUEUED.name() : task.getTaskStage().name(),
                task.getTriggerSource() == null ? IndexingTaskTriggerSource.SUBMIT.name() : task.getTriggerSource().name(),
                document.getId(),
                document.getDocumentCode(),
                kbCode,
                task.getParentTaskId(),
                task.getParserName(),
                task.getChunkCount(),
                task.getEmbeddedChunkCount(),
                task.getRetryCount(),
                task.getMaxRetryCount(),
                task.getErrorMessage(),
                task.getCreatedBy(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getLastHeartbeatAt(),
                task.getRecoveredAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private IndexingTaskEntity createTask(DocumentEntity document,
                                          Long parentTaskId,
                                          IndexingTaskTriggerSource triggerSource,
                                          String operator) {
        IndexingTaskEntity task = new IndexingTaskEntity();
        OffsetDateTime now = OffsetDateTime.now();
        task.setId(snowflakeIdGenerator.nextId());
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(document.getId());
        task.setParentTaskId(parentTaskId);
        task.setTaskType(TASK_TYPE_DOCUMENT_INDEXING);
        task.setStatus(IndexingTaskStatus.QUEUED);
        task.setTaskStage(IndexingTaskStage.QUEUED);
        task.setTriggerSource(triggerSource);
        task.setRetryCount(0);
        task.setMaxRetryCount(Math.max(1, ragIndexingProperties.getMaxRetryCount()));
        task.setStartedAt(now);
        task.setLastHeartbeatAt(now);
        task.setCreatedBy(operator);
        indexingTaskRepository.insert(task);
        return task;
    }

    private IndexingTaskEntity createRetryTask(DocumentEntity document,
                                               IndexingTaskEntity sourceTask,
                                               IndexingTaskTriggerSource triggerSource,
                                               String operator) {
        IndexingTaskEntity retryTask = createTask(document, sourceTask.getId(), triggerSource, operator);
        retryTask.setRetryCount((sourceTask.getRetryCount() == null ? 0 : sourceTask.getRetryCount()) + 1);
        retryTask.setMaxRetryCount(sourceTask.getMaxRetryCount() == null
                ? Math.max(1, ragIndexingProperties.getMaxRetryCount())
                : sourceTask.getMaxRetryCount());
        retryTask.setParserName(sourceTask.getParserName());
        retryTask.setChunkCount(sourceTask.getChunkCount());
        retryTask.setEmbeddedChunkCount(sourceTask.getEmbeddedChunkCount());
        indexingTaskRepository.updateById(retryTask);
        return retryTask;
    }

    private void markRecovered(IndexingTaskEntity sourceTask, String message) {
        OffsetDateTime now = OffsetDateTime.now();
        sourceTask.setRecoveredAt(now);
        sourceTask.setFinishedAt(now);
        sourceTask.setLastHeartbeatAt(now);
        sourceTask.setStatus(IndexingTaskStatus.FAILED);
        sourceTask.setErrorMessage(truncate(message));
        indexingTaskRepository.updateById(sourceTask);
    }

    private void touchHeartbeat(IndexingTaskEntity task, String errorMessage) {
        task.setErrorMessage(errorMessage);
        task.setLastHeartbeatAt(OffsetDateTime.now());
        indexingTaskRepository.updateById(task);
    }

    private void dispatch(Long taskId) {
        indexingExecutor.execute(() -> runAsync(taskId));
    }

    private void ensureKnowledgeBaseActive(KnowledgeBaseEntity knowledgeBase) {
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BusinessException("Knowledge base is inactive: " + knowledgeBase.getKbCode());
        }
    }

    private String normalizeOperator(String operator) {
        if (operator == null) {
            return "system";
        }
        String normalized = operator.trim();
        return normalized.isEmpty() ? "system" : normalized;
    }

    private String truncate(String message) {
        String normalized = Objects.requireNonNullElse(message, "Unknown indexing error");
        if (normalized.length() <= 1024) {
            return normalized;
        }
        return normalized.substring(0, 1024);
    }
}
