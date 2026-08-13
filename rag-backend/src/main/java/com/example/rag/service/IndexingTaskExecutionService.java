package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagIndexingProperties;
import com.example.rag.model.response.DocumentEmbeddingResponse;
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
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 执行一条已被当前实例 Claim 的索引任务。 */
@Service
public class IndexingTaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(IndexingTaskExecutionService.class);
    private final IndexingTaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentProcessingService processingService;
    private final DocumentEmbeddingService embeddingService;
    private final RagIndexingProperties properties;
    private final ScheduledExecutorService heartbeatExecutor;

    public IndexingTaskExecutionService(IndexingTaskRepository taskRepository,
                                        DocumentRepository documentRepository,
                                        KnowledgeBaseRepository knowledgeBaseRepository,
                                        DocumentProcessingService processingService,
                                        DocumentEmbeddingService embeddingService,
                                        RagIndexingProperties properties,
                                        ScheduledExecutorService heartbeatExecutor) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.processingService = processingService;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    public void execute(IndexingTaskEntity task) {
        AtomicBoolean ownsLease = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = startHeartbeat(task, ownsLease);
        try {
            DocumentEntity document = documentRepository.findById(task.getDocumentId())
                    .orElseThrow(() -> new IllegalStateException("Document not found for task " + task.getId()));
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(task.getKnowledgeBaseId())
                    .orElseThrow(() -> new IllegalStateException("Knowledge base not found for task " + task.getId()));
            putMdc(task, document, knowledgeBase);
            log.info(StructuredLogMessage.of("indexing.task.claimed")
                    .field("taskId", task.getId())
                    .field("instanceId", task.getOwnerInstanceId())
                    .field("leaseVersion", task.getLeaseVersion())
                    .build());

            requireOwnership(ownsLease, task);
            DocumentProcessResponse processed = processingService.processForIndexing(
                    Objects.requireNonNull(knowledgeBase.getKbCode()),
                    Objects.requireNonNull(document.getDocumentCode()),
                    task.getCreatedBy(),
                    () -> requireOwnership(ownsLease, task));
            requireOwnedUpdate(taskRepository.updateOwnedStage(
                    task.getId(), task.getOwnerInstanceId(), task.getLeaseVersion(),
                    "DOCUMENT_EMBEDDING", processed.parserName(), processed.chunkCount(),
                    now(), leaseUntil()), task, ownsLease);

            requireOwnership(ownsLease, task);
            DocumentEmbeddingResponse embedded = embeddingService.embedForIndexing(
                    knowledgeBase.getKbCode(), document.getDocumentCode(),
                    () -> requireOwnership(ownsLease, task));
            requireOwnedUpdate(taskRepository.completeOwned(
                    task.getId(), task.getOwnerInstanceId(), task.getLeaseVersion(),
                    Math.toIntExact(embedded.totalEmbeddedChunkCount()), now()), task, ownsLease);
            log.info(StructuredLogMessage.of("indexing.task.succeeded")
                    .field("taskId", task.getId())
                    .field("instanceId", task.getOwnerInstanceId())
                    .build());
        } catch (RuntimeException ex) {
            boolean recorded = ownsLease.get() && taskRepository.failOwned(
                    task.getId(), task.getOwnerInstanceId(), task.getLeaseVersion(), truncate(ex.getMessage()), now());
            log.warn(StructuredLogMessage.of(recorded ? "indexing.task.failed" : "indexing.task.fenced")
                    .field("taskId", task.getId())
                    .field("instanceId", task.getOwnerInstanceId())
                    .field("leaseVersion", task.getLeaseVersion())
                    .field("message", ex.getMessage())
                    .build());
        } finally {
            heartbeat.cancel(false);
            clearMdc();
        }
    }

    private ScheduledFuture<?> startHeartbeat(IndexingTaskEntity task, AtomicBoolean ownsLease) {
        long intervalMillis = properties.getWorker().getHeartbeatInterval().toMillis();
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!ownsLease.get()) {
                return;
            }
            try {
                boolean renewed = taskRepository.heartbeat(task.getId(), task.getOwnerInstanceId(),
                        task.getLeaseVersion(), now(), leaseUntil());
                if (!renewed) {
                    ownsLease.set(false);
                }
            } catch (RuntimeException ex) {
                log.warn(StructuredLogMessage.of("indexing.task.heartbeat_failed")
                        .field("taskId", task.getId())
                        .field("instanceId", task.getOwnerInstanceId())
                        .field("message", ex.getMessage())
                        .build());
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void requireOwnedUpdate(boolean updated, IndexingTaskEntity task, AtomicBoolean ownsLease) {
        if (!updated) {
            ownsLease.set(false);
            throw new IllegalStateException("Task ownership lost: " + task.getId());
        }
    }

    private void requireOwnership(AtomicBoolean ownsLease, IndexingTaskEntity task) {
        if (!ownsLease.get() || !taskRepository.isOwned(
                task.getId(), task.getOwnerInstanceId(), task.getLeaseVersion())) {
            ownsLease.set(false);
            throw new IllegalStateException("Task ownership lost: " + task.getId());
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private OffsetDateTime leaseUntil() {
        return now().plus(properties.getWorker().getLeaseDuration());
    }

    private String truncate(String message) {
        String value = Objects.requireNonNullElse(message, "Unknown indexing error");
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    private void putMdc(IndexingTaskEntity task, DocumentEntity document, KnowledgeBaseEntity knowledgeBase) {
        MDC.put("instanceId", task.getOwnerInstanceId());
        MDC.put("taskId", String.valueOf(task.getId()));
        MDC.put("kbCode", knowledgeBase.getKbCode());
        MDC.put("documentCode", document.getDocumentCode());
    }

    private void clearMdc() {
        MDC.remove("documentCode");
        MDC.remove("kbCode");
        MDC.remove("taskId");
        MDC.remove("instanceId");
    }
}
