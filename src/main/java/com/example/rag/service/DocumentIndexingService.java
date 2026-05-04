package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.IndexingTaskStage;
import com.example.rag.model.enums.IndexingTaskStatus;
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
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final Executor indexingExecutor;

    public DocumentIndexingService(KnowledgeBaseRepository knowledgeBaseRepository,
                                   DocumentRepository documentRepository,
                                   IndexingTaskRepository indexingTaskRepository,
                                   DocumentProcessingService documentProcessingService,
                                   DocumentEmbeddingService documentEmbeddingService,
                                   SnowflakeIdGenerator snowflakeIdGenerator,
                                   @Qualifier("indexingExecutor") Executor indexingExecutor) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.indexingTaskRepository = indexingTaskRepository;
        this.documentProcessingService = documentProcessingService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
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

        IndexingTaskEntity task = new IndexingTaskEntity();
        task.setId(snowflakeIdGenerator.nextId());
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(document.getId());
        task.setTaskType(TASK_TYPE_DOCUMENT_INDEXING);
        task.setStatus(IndexingTaskStatus.QUEUED);
        task.setTaskStage(IndexingTaskStage.QUEUED);
        task.setStartedAt(OffsetDateTime.now());
        task.setCreatedBy(normalizeOperator(operator));
        indexingTaskRepository.insert(task);

        indexingExecutor.execute(() -> runAsync(task.getId(), kbCode, documentCode, operator));
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

    private void runAsync(Long taskId, String kbCode, String documentCode, String operator) {
        IndexingTaskEntity task = indexingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Indexing task not found: " + taskId));
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        try {
            task.setStatus(IndexingTaskStatus.RUNNING);
            task.setTaskStage(IndexingTaskStage.DOCUMENT_PROCESSING);
            task.setErrorMessage(null);
            indexingTaskRepository.updateById(task);

            DocumentProcessResponse processResponse = documentProcessingService.process(kbCode, documentCode, operator);
            task.setParserName(processResponse.parserName());
            task.setChunkCount(processResponse.chunkCount());
            task.setTaskStage(IndexingTaskStage.DOCUMENT_EMBEDDING);
            indexingTaskRepository.updateById(task);

            DocumentEmbeddingResponse embeddingResponse = documentEmbeddingService.embed(kbCode, documentCode);
            task.setEmbeddedChunkCount(Math.toIntExact(embeddingResponse.totalEmbeddedChunkCount()));
            task.setStatus(IndexingTaskStatus.SUCCEEDED);
            task.setTaskStage(IndexingTaskStage.COMPLETED);
            task.setFinishedAt(OffsetDateTime.now());
            indexingTaskRepository.updateById(task);
        } catch (RuntimeException ex) {
            task.setStatus(IndexingTaskStatus.FAILED);
            task.setErrorMessage(truncate(ex.getMessage()));
            task.setFinishedAt(OffsetDateTime.now());
            indexingTaskRepository.updateById(task);
        }
    }

    private DocumentIndexingTaskResponse toResponse(IndexingTaskEntity task, DocumentEntity document, String kbCode) {
        return new DocumentIndexingTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus() == null ? IndexingTaskStatus.QUEUED.name() : task.getStatus().name(),
                task.getTaskStage() == null ? IndexingTaskStage.QUEUED.name() : task.getTaskStage().name(),
                document.getId(),
                document.getDocumentCode(),
                kbCode,
                task.getParserName(),
                task.getChunkCount(),
                task.getEmbeddedChunkCount(),
                task.getErrorMessage(),
                task.getCreatedBy(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
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
