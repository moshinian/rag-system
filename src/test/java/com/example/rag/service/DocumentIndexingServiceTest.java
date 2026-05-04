package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagIndexingProperties;
import com.example.rag.common.id.SnowflakeIdGenerator;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private IndexingTaskRepository indexingTaskRepository;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private DocumentEmbeddingService documentEmbeddingService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private final Executor directExecutor = Runnable::run;

    @Test
    void submitShouldQueueAndCompleteAsyncIndexingTask() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.UPLOADED);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(knowledgeBaseRepository.findById(100L)).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(documentRepository.findById(200L)).thenReturn(Optional.of(document));
        when(indexingTaskRepository.existsActiveTask(200L, "DOCUMENT_INDEXING")).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(999L);
        when(indexingTaskRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.findById(999L)).thenAnswer(invocation -> {
            IndexingTaskEntity task = new IndexingTaskEntity();
            task.setId(999L);
            task.setDocumentId(200L);
            task.setKnowledgeBaseId(100L);
            task.setTaskType("DOCUMENT_INDEXING");
            task.setStatus(IndexingTaskStatus.QUEUED);
            task.setTaskStage(IndexingTaskStage.QUEUED);
            task.setCreatedBy("tester");
            task.setStartedAt(OffsetDateTime.now());
            return Optional.of(task);
        });
        when(documentProcessingService.process("settlement-kb", "DOC-1", "tester"))
                .thenReturn(new DocumentProcessResponse(200L, "DOC-1", "settlement-kb", "md", "INDEXED", 3, "markdown", OffsetDateTime.now()));
        when(documentEmbeddingService.embed("settlement-kb", "DOC-1"))
                .thenReturn(new DocumentEmbeddingResponse(200L, "DOC-1", "settlement-kb", "bge", 512, 16, 3, 0, 3, OffsetDateTime.now()));
        when(indexingTaskRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentIndexingService service = new DocumentIndexingService(
                knowledgeBaseRepository,
                documentRepository,
                indexingTaskRepository,
                documentProcessingService,
                documentEmbeddingService,
                snowflakeIdGenerator,
                createIndexingProperties(),
                directExecutor
        );

        DocumentIndexingTaskResponse response = service.submit("settlement-kb", "DOC-1", "tester");

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.taskStage()).isEqualTo("QUEUED");

        ArgumentCaptor<IndexingTaskEntity> taskCaptor = ArgumentCaptor.forClass(IndexingTaskEntity.class);
        verify(indexingTaskRepository, atLeastOnce()).updateById(taskCaptor.capture());
        verify(indexingTaskRepository, atLeast(3)).updateById(any());
        IndexingTaskEntity finalTask = taskCaptor.getValue();
        assertThat(finalTask.getStatus()).isEqualTo(IndexingTaskStatus.SUCCEEDED);
        assertThat(finalTask.getTaskStage()).isEqualTo(IndexingTaskStage.COMPLETED);
        assertThat(finalTask.getChunkCount()).isEqualTo(3);
        assertThat(finalTask.getEmbeddedChunkCount()).isEqualTo(3);
    }

    @Test
    void retryShouldCreateChildTaskForFailedIndexingTask() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.FAILED);

        IndexingTaskEntity failedTask = new IndexingTaskEntity();
        failedTask.setId(500L);
        failedTask.setKnowledgeBaseId(100L);
        failedTask.setDocumentId(200L);
        failedTask.setTaskType("DOCUMENT_INDEXING");
        failedTask.setStatus(IndexingTaskStatus.FAILED);
        failedTask.setTaskStage(IndexingTaskStage.DOCUMENT_EMBEDDING);
        failedTask.setRetryCount(0);
        failedTask.setMaxRetryCount(3);
        failedTask.setCreatedBy("tester");

        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(documentRepository.findById(200L)).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findById(100L)).thenReturn(Optional.of(knowledgeBase));
        when(indexingTaskRepository.findById(500L)).thenReturn(Optional.of(failedTask));
        when(indexingTaskRepository.existsActiveTask(200L, "DOCUMENT_INDEXING")).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(999L);
        when(indexingTaskRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.findById(999L)).thenAnswer(invocation -> {
            IndexingTaskEntity task = new IndexingTaskEntity();
            task.setId(999L);
            task.setDocumentId(200L);
            task.setKnowledgeBaseId(100L);
            task.setTaskType("DOCUMENT_INDEXING");
            task.setStatus(IndexingTaskStatus.QUEUED);
            task.setTaskStage(IndexingTaskStage.QUEUED);
            task.setTriggerSource(IndexingTaskTriggerSource.MANUAL_RETRY);
            task.setRetryCount(1);
            task.setMaxRetryCount(3);
            task.setCreatedBy("tester");
            task.setStartedAt(OffsetDateTime.now());
            task.setLastHeartbeatAt(OffsetDateTime.now());
            return Optional.of(task);
        });
        when(documentProcessingService.process("settlement-kb", "DOC-1", "tester"))
                .thenReturn(new DocumentProcessResponse(200L, "DOC-1", "settlement-kb", "md", "INDEXED", 3, "markdown", OffsetDateTime.now()));
        when(documentEmbeddingService.embed("settlement-kb", "DOC-1"))
                .thenReturn(new DocumentEmbeddingResponse(200L, "DOC-1", "settlement-kb", "bge", 512, 16, 3, 0, 3, OffsetDateTime.now()));

        DocumentIndexingService service = new DocumentIndexingService(
                knowledgeBaseRepository,
                documentRepository,
                indexingTaskRepository,
                documentProcessingService,
                documentEmbeddingService,
                snowflakeIdGenerator,
                createIndexingProperties(),
                directExecutor
        );

        DocumentIndexingTaskResponse response = service.retry("settlement-kb", "DOC-1", 500L, "tester");

        assertThat(response.parentTaskId()).isEqualTo(500L);
        assertThat(response.triggerSource()).isEqualTo("MANUAL_RETRY");
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(failedTask.getRecoveredAt()).isNotNull();
    }

    @Test
    void submitShouldRejectWhenAnotherActiveTaskExists() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.UPLOADED);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(indexingTaskRepository.existsActiveTask(200L, "DOCUMENT_INDEXING")).thenReturn(true);

        DocumentIndexingService service = new DocumentIndexingService(
                knowledgeBaseRepository,
                documentRepository,
                indexingTaskRepository,
                documentProcessingService,
                documentEmbeddingService,
                snowflakeIdGenerator,
                createIndexingProperties(),
                directExecutor
        );

        assertThatThrownBy(() -> service.submit("settlement-kb", "DOC-1", "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active indexing task");
    }

    @Test
    void recoverStaleTasksShouldCreateRecoveryTask() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.UPLOADED);

        IndexingTaskEntity staleTask = new IndexingTaskEntity();
        staleTask.setId(500L);
        staleTask.setKnowledgeBaseId(100L);
        staleTask.setDocumentId(200L);
        staleTask.setTaskType("DOCUMENT_INDEXING");
        staleTask.setStatus(IndexingTaskStatus.RUNNING);
        staleTask.setTaskStage(IndexingTaskStage.DOCUMENT_PROCESSING);
        staleTask.setRetryCount(0);
        staleTask.setMaxRetryCount(3);
        staleTask.setCreatedBy("tester");

        when(indexingTaskRepository.findRecoverableTasks(any(), any(), anyInt())).thenReturn(List.of(staleTask));
        when(indexingTaskRepository.existsOtherActiveTask(200L, "DOCUMENT_INDEXING", 500L)).thenReturn(false);
        when(documentRepository.findById(200L)).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findById(100L)).thenReturn(Optional.of(knowledgeBase));
        when(snowflakeIdGenerator.nextId()).thenReturn(999L);
        when(indexingTaskRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.findById(999L)).thenAnswer(invocation -> {
            IndexingTaskEntity task = new IndexingTaskEntity();
            task.setId(999L);
            task.setDocumentId(200L);
            task.setKnowledgeBaseId(100L);
            task.setTaskType("DOCUMENT_INDEXING");
            task.setStatus(IndexingTaskStatus.QUEUED);
            task.setTaskStage(IndexingTaskStage.QUEUED);
            task.setTriggerSource(IndexingTaskTriggerSource.RECOVERY);
            task.setRetryCount(1);
            task.setMaxRetryCount(3);
            task.setCreatedBy("tester");
            task.setStartedAt(OffsetDateTime.now());
            task.setLastHeartbeatAt(OffsetDateTime.now());
            return Optional.of(task);
        });
        when(documentProcessingService.process("settlement-kb", "DOC-1", "tester"))
                .thenReturn(new DocumentProcessResponse(200L, "DOC-1", "settlement-kb", "md", "INDEXED", 3, "markdown", OffsetDateTime.now()));
        when(documentEmbeddingService.embed("settlement-kb", "DOC-1"))
                .thenReturn(new DocumentEmbeddingResponse(200L, "DOC-1", "settlement-kb", "bge", 512, 16, 3, 0, 3, OffsetDateTime.now()));

        DocumentIndexingService service = new DocumentIndexingService(
                knowledgeBaseRepository,
                documentRepository,
                indexingTaskRepository,
                documentProcessingService,
                documentEmbeddingService,
                snowflakeIdGenerator,
                createIndexingProperties(),
                directExecutor
        );

        service.recoverStaleTasks();

        assertThat(staleTask.getRecoveredAt()).isNotNull();
        assertThat(staleTask.getErrorMessage()).contains("Recovered by task");
    }

    @Test
    void listTasksShouldReturnMostRecentTaskHistory() {
        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setDocumentCode("DOC-1");

        IndexingTaskEntity task = new IndexingTaskEntity();
        task.setId(999L);
        task.setTaskType("DOCUMENT_INDEXING");
        task.setStatus(IndexingTaskStatus.SUCCEEDED);
        task.setTaskStage(IndexingTaskStage.COMPLETED);
        task.setChunkCount(6);
        task.setEmbeddedChunkCount(6);

        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(indexingTaskRepository.findByDocumentIdOrderByCreatedAtDesc(200L)).thenReturn(List.of(task));

        DocumentIndexingService service = new DocumentIndexingService(
                knowledgeBaseRepository,
                documentRepository,
                indexingTaskRepository,
                documentProcessingService,
                documentEmbeddingService,
                snowflakeIdGenerator,
                createIndexingProperties(),
                directExecutor
        );

        assertThat(service.listTasks("settlement-kb", "DOC-1"))
                .singleElement()
                .extracting(DocumentIndexingTaskResponse::taskStage)
                .isEqualTo("COMPLETED");
    }

    private RagIndexingProperties createIndexingProperties() {
        RagIndexingProperties properties = new RagIndexingProperties();
        properties.setMaxRetryCount(3);
        properties.getRecovery().setEnabled(true);
        properties.getRecovery().setStaleAfterSeconds(60);
        properties.getRecovery().setScanLimit(10);
        return properties;
    }
}
