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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;

/**
 * 文档异步索引服务。
 */
@Service
public class DocumentIndexingService {
    private static final String TASK_TYPE_DOCUMENT_INDEXING = "DOCUMENT_INDEXING";
    private static final int TASK_LOOKUP_MAX_ATTEMPTS = 5;
    private static final long TASK_LOOKUP_RETRY_NANOS = 50_000_000L;
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RagIndexingProperties ragIndexingProperties;
    private final Executor indexingExecutor;

    /** 构造DocumentIndexingService。 */
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
        // 已被手工下线的文档不应再进入后台索引流程。
        if (document.getStatus() == DocumentStatus.DISABLED) {
            throw new BusinessException("Document is disabled and cannot be indexed: " + documentCode);
        }
        // 这里先做一次显式校验，给调用方稳定的业务错误；真正兜底仍依赖数据库唯一约束。
        if (indexingTaskRepository.existsActiveTask(document.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("An active indexing task already exists for document: " + documentCode);
        }

        IndexingTaskEntity task = createTask(document, null, IndexingTaskTriggerSource.SUBMIT, normalizeOperator(operator));
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
        return indexingTaskRepository.findByDocumentIdAndTaskTypeOrderByCreatedAtDesc(
                        document.getId(),
                        TASK_TYPE_DOCUMENT_INDEXING
                ).stream()
                .map(task -> toResponse(task, document, kbCode))
                .toList();
    }

    /** 手动重试失败任务。 */
    public DocumentIndexingTaskResponse retry(String kbCode, String documentCode, Long taskId, String operator) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        // 重试遵循当前文档可用性，而不是历史失败时的旧状态。
        if (document.getStatus() == DocumentStatus.DISABLED) {
            throw new BusinessException("Document is disabled and cannot be re-indexed: " + documentCode);
        }
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

    /** 在知识库恢复使用后，批量重试每篇文档最近一次可重试的失败索引任务。 */
    public BatchRetryIndexingResult retryLatestFailedTasksInKnowledgeBase(String kbCode, String operator) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);

        List<DocumentEntity> documents = documentRepository.findByKnowledgeBaseId(knowledgeBase.getId());
        int retriedTaskCount = 0;
        int skippedDisabledDocumentCount = 0;
        int skippedActiveTaskDocumentCount = 0;
        int skippedRetryLimitDocumentCount = 0;
        List<String> retriedDocumentCodes = new java.util.ArrayList<>();

        for (DocumentEntity document : documents) {
            if (document.getStatus() == DocumentStatus.DISABLED) {
                skippedDisabledDocumentCount++;
                continue;
            }
            // 恢复补偿只补最近一次失败任务，避免对同一文档堆积多条活跃任务。
            if (indexingTaskRepository.existsActiveTask(document.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
                skippedActiveTaskDocumentCount++;
                continue;
            }
            IndexingTaskEntity latestTask = indexingTaskRepository
                    .findByDocumentIdAndTaskTypeOrderByCreatedAtDesc(document.getId(), TASK_TYPE_DOCUMENT_INDEXING)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (latestTask == null || latestTask.getStatus() != IndexingTaskStatus.FAILED) {
                continue;
            }
            if (latestTask.getRetryCount() != null
                    && latestTask.getMaxRetryCount() != null
                    && latestTask.getRetryCount() >= latestTask.getMaxRetryCount()) {
                skippedRetryLimitDocumentCount++;
                continue;
            }

            IndexingTaskEntity retryTask = createRetryTask(
                    document,
                    latestTask,
                    IndexingTaskTriggerSource.MANUAL_RETRY,
                    normalizeOperator(operator)
            );
            retriedTaskCount++;
            retriedDocumentCodes.add(document.getDocumentCode());
        }

        log.info(StructuredLogMessage.of("indexing.task.batch_retry_submitted")
                .field("kbCode", kbCode)
                .field("retriedTaskCount", retriedTaskCount)
                .field("skippedDisabledDocumentCount", skippedDisabledDocumentCount)
                .field("skippedActiveTaskDocumentCount", skippedActiveTaskDocumentCount)
                .field("skippedRetryLimitDocumentCount", skippedRetryLimitDocumentCount)
                .build());
        return new BatchRetryIndexingResult(
                retriedTaskCount,
                skippedDisabledDocumentCount,
                skippedActiveTaskDocumentCount,
                skippedRetryLimitDocumentCount,
                List.copyOf(retriedDocumentCodes)
        );
    }

    /** 定时扫描卡住的队列中/运行中任务，并重新投递。 */
    public void recoverStaleTasks() {
        if (!ragIndexingProperties.getRecovery().isEnabled()) {
            return;
        }
        // staleAfterSeconds 允许配置得过小，这里仍保留一个 30s 下限，避免误扫刚提交的任务。
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

    /** 对单条卡住任务执行恢复判断，并在必要时创建新的恢复任务。 */
    private void recoverStaleTask(IndexingTaskEntity staleTask) {
        // 如果同文档已经有别的活跃任务，就说明这条旧任务不该再被恢复。
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
            // 恢复扫描和手动重试共用同一套最大重试边界，避免后台无限自旋。
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

        // 先把陈旧任务移出 active 集合，再创建恢复任务，避免唯一索引把恢复任务本身挡住。
        markRecovered(staleTask, "Recovered by recovery scan");
        IndexingTaskEntity retryTask = createRetryTask(document, staleTask, IndexingTaskTriggerSource.RECOVERY, staleTask.getCreatedBy());
        staleTask.setErrorMessage(truncate("Recovered by task " + retryTask.getId()));
        indexingTaskRepository.updateById(staleTask);
        log.info(StructuredLogMessage.of("indexing.recovery.dispatched")
                .field("taskId", staleTask.getId())
                .field("recoveryTaskId", retryTask.getId())
                .field("documentId", staleTask.getDocumentId())
                .field("retryCount", retryTask.getRetryCount())
                .build());
    }

    /** 在线程池中执行实际索引流程，并持续更新任务状态。 */
    private void runAsync(Long taskId) {
        // 提交和消费不在同一事务里，短暂轮询是为了规避“任务刚插入但读还未可见”的窗口。
        IndexingTaskEntity task = waitForTaskRecord(taskId)
                .orElseThrow(() -> new BusinessException("Indexing task not found after dispatch: " + taskId));
        try {
            DocumentEntity document = documentRepository.findById(task.getDocumentId())
                    .orElseThrow(() -> new BusinessException("Document not found for indexing task: " + taskId));
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(task.getKnowledgeBaseId())
                    .orElseThrow(() -> new BusinessException("Knowledge base not found for indexing task: " + taskId));
            String operator = task.getCreatedBy();
            MDC.put("taskId", String.valueOf(task.getId()));
            MDC.put("kbCode", knowledgeBase.getKbCode());
            MDC.put("documentCode", document.getDocumentCode());
            log.info(StructuredLogMessage.of("indexing.task.started")
                    .field("taskId", task.getId())
                    .field("kbCode", knowledgeBase.getKbCode())
                    .field("documentCode", document.getDocumentCode())
                    .field("triggerSource", task.getTriggerSource())
                    .field("retryCount", task.getRetryCount())
                    .build());
            // 任务状态和阶段分开维护，便于外部区分“正在跑”和“跑到哪一步”。
            task.setStatus(IndexingTaskStatus.RUNNING);
            task.setTaskStage(IndexingTaskStage.DOCUMENT_PROCESSING);
            task.setErrorMessage(null);
            touchHeartbeat(task, null);

            String kbCode = Objects.requireNonNull(knowledgeBase.getKbCode(), "knowledgeBase.kbCode must not be null");
            String documentCode = Objects.requireNonNull(document.getDocumentCode(), "document.documentCode must not be null");
            DocumentProcessResponse processResponse = documentProcessingService.processForIndexing(
                    kbCode,
                    documentCode,
                    operator
            );
            task.setParserName(processResponse.parserName());
            task.setChunkCount(processResponse.chunkCount());
            // 处理阶段成功后立刻刷新心跳，避免长时间 embedding 被恢复扫描误判为卡住。
            task.setTaskStage(IndexingTaskStage.DOCUMENT_EMBEDDING);
            touchHeartbeat(task, null);

            DocumentEmbeddingResponse embeddingResponse = documentEmbeddingService.embedForIndexing(
                    knowledgeBase.getKbCode(),
                    document.getDocumentCode()
            );
            task.setEmbeddedChunkCount(Math.toIntExact(embeddingResponse.totalEmbeddedChunkCount()));
            task.setStatus(IndexingTaskStatus.SUCCEEDED);
            task.setTaskStage(IndexingTaskStage.COMPLETED);
            task.setFinishedAt(OffsetDateTime.now());
            touchHeartbeat(task, null);
            markParentRecoveredAfterChildSuccess(task);
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
            markParentAfterChildFailure(task, ex.getMessage());
            log.warn(StructuredLogMessage.of("indexing.task.failed")
                    .field("taskId", task.getId())
                    .field("taskStage", task.getTaskStage())
                    .field("message", ex.getMessage())
                    .build());
        } finally {
            MDC.remove("documentCode");
            MDC.remove("kbCode");
            MDC.remove("taskId");
        }
    }

    /** 把任务实体转换成接口返回结构。 */
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

    /** 创建新的索引任务记录，并初始化为排队状态。 */
    private IndexingTaskEntity createTask(DocumentEntity document,
                                          Long parentTaskId,
                                          IndexingTaskTriggerSource triggerSource,
                                          String operator) {
        try {
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
            task.setStartedAt(null);
            task.setLastHeartbeatAt(null);
            task.setCreatedBy(operator);
            indexingTaskRepository.insert(task);
            return task;
        } catch (DataIntegrityViolationException ex) {
            throw toActiveTaskConflict(document.getDocumentCode(), ex);
        }
    }

    /** 基于原任务创建新的重试任务，并继承必要的上下文信息。 */
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

    /** 把原任务标记为已被新的恢复任务接管。 */
    private void markRecovered(IndexingTaskEntity sourceTask, String message) {
        OffsetDateTime now = OffsetDateTime.now();
        sourceTask.setRecoveredAt(now);
        sourceTask.setFinishedAt(now);
        sourceTask.setLastHeartbeatAt(now);
        sourceTask.setStatus(IndexingTaskStatus.FAILED);
        sourceTask.setErrorMessage(truncate(message));
        indexingTaskRepository.updateById(sourceTask);
    }

    /** 刷新任务心跳，并按需更新错误信息。 */
    private void touchHeartbeat(IndexingTaskEntity task, String errorMessage) {
        task.setErrorMessage(errorMessage);
        task.setLastHeartbeatAt(OffsetDateTime.now());
        indexingTaskRepository.updateById(task);
    }

    /** 把任务投递到异步执行器。 */
    private void dispatch(Long taskId) {
        try {
            indexingExecutor.execute(() -> {
                try {
                    runAsync(taskId);
                } catch (RuntimeException ex) {
                    log.warn(StructuredLogMessage.of("indexing.task.dispatch_failed")
                            .field("taskId", taskId)
                            .field("message", ex.getMessage())
                            .build());
                    markTaskFailedAfterDispatch(taskId, ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            IndexingTaskEntity task = indexingTaskRepository.findById(taskId)
                    .orElseThrow(() -> new BusinessException("Indexing task not found: " + taskId));
            task.setStatus(IndexingTaskStatus.FAILED);
            task.setTaskStage(IndexingTaskStage.QUEUED);
            task.setFinishedAt(OffsetDateTime.now());
            touchHeartbeat(task, truncate("Failed to dispatch indexing task: " + ex.getMessage()));
            throw new BusinessException("Indexing executor is busy, please retry later");
        }
    }

    /** 轮询等待任务记录可见。 */
    private java.util.Optional<IndexingTaskEntity> waitForTaskRecord(Long taskId) {
        for (int attempt = 1; attempt <= TASK_LOOKUP_MAX_ATTEMPTS; attempt++) {
            java.util.Optional<IndexingTaskEntity> task = indexingTaskRepository.findById(taskId);
            if (task.isPresent()) {
                return task;
            }
            if (attempt < TASK_LOOKUP_MAX_ATTEMPTS) {
                LockSupport.parkNanos(TASK_LOOKUP_RETRY_NANOS);
            }
        }
        return java.util.Optional.empty();
    }

    /** 在投递失败后把任务标记为失败。 */
    private void markTaskFailedAfterDispatch(Long taskId, RuntimeException ex) {
        indexingTaskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(IndexingTaskStatus.FAILED);
            task.setFinishedAt(OffsetDateTime.now());
            touchHeartbeat(task, truncate("Async dispatch failed: " + ex.getMessage()));
        });
    }

    /** 只有启用状态的知识库才允许继续索引。 */
    private void ensureKnowledgeBaseActive(KnowledgeBaseEntity knowledgeBase) {
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BusinessException("Knowledge base is inactive: " + knowledgeBase.getKbCode());
        }
    }

    /** 统一规范操作人字段，避免出现空字符串。 */
    private String normalizeOperator(String operator) {
        if (operator == null) {
            return "system";
        }
        String normalized = operator.trim();
        return normalized.isEmpty() ? "system" : normalized;
    }

    /** 截断错误信息，避免超出数据库字段长度。 */
    private String truncate(String message) {
        String normalized = Objects.requireNonNullElse(message, "Unknown indexing error");
        if (normalized.length() <= 1024) {
            return normalized;
        }
        return normalized.substring(0, 1024);
    }

    /** 把数据库唯一约束冲突翻译成稳定业务语义，避免并发提交泄漏底层异常。 */
    private BusinessException toActiveTaskConflict(String documentCode, DataIntegrityViolationException ex) {
        return new BusinessException("An active indexing task already exists for document: " + documentCode);
    }

    /** 子任务真正成功后，再把父任务标记为已被新任务成功接管。 */
    private void markParentRecoveredAfterChildSuccess(IndexingTaskEntity task) {
        if (task.getParentTaskId() == null) {
            return;
        }
        indexingTaskRepository.findById(task.getParentTaskId()).ifPresent(parentTask -> {
            String message = task.getTriggerSource() == IndexingTaskTriggerSource.RECOVERY
                    ? "Recovered by task " + task.getId()
                    : "Manually retried by task " + task.getId();
            markRecovered(parentTask, message);
        });
    }

    /** 子任务失败时，避免把父任务错误保留成“已恢复”。 */
    private void markParentAfterChildFailure(IndexingTaskEntity task, String errorMessage) {
        if (task.getParentTaskId() == null) {
            return;
        }
        indexingTaskRepository.findById(task.getParentTaskId()).ifPresent(parentTask -> {
            if (task.getTriggerSource() == IndexingTaskTriggerSource.MANUAL_RETRY) {
                parentTask.setErrorMessage(truncate("Retry task " + task.getId() + " failed: " + errorMessage));
                parentTask.setRecoveredAt(null);
                indexingTaskRepository.updateById(parentTask);
                return;
            }
            if (task.getTriggerSource() == IndexingTaskTriggerSource.RECOVERY) {
                parentTask.setErrorMessage(truncate("Recovery task " + task.getId() + " failed: " + errorMessage));
                parentTask.setRecoveredAt(null);
                indexingTaskRepository.updateById(parentTask);
            }
        });
    }

    /** 知识库级批量失败任务补偿结果。 */
    public record BatchRetryIndexingResult(
            int retriedTaskCount,
            int skippedDisabledDocumentCount,
            int skippedActiveTaskDocumentCount,
            int skippedRetryLimitDocumentCount,
            List<String> retriedDocumentCodes
    ) {
    }
}
