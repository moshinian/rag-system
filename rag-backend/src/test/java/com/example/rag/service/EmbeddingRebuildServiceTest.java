package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.CacheNames;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.model.enums.EmbeddingRebuildRunStatus;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.EmbeddingRebuildRunRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.EmbeddingRebuildRunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingRebuildServiceTest {

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private EmbeddingConfigurationStateService embeddingConfigurationStateService;

    @Mock
    private EmbeddingRebuildRunRepository embeddingRebuildRunRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentEmbeddingService documentEmbeddingService;

    @Mock
    private IndexingTaskRepository indexingTaskRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private Executor indexingExecutor;

    @Mock
    private CacheManager cacheManager;

    private Cache qaReadinessCache;
    private Cache qaRetrievalCache;
    private Cache documentChunksCache;
    private EmbeddingRebuildService service;

    @BeforeEach
    void setUp() {
        RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
        embeddingProperties.setProvider("aliyun-bailian-openai-compatible");
        embeddingProperties.setModel("text-embedding-v4");
        embeddingProperties.setVectorDimensions(1024);
        embeddingProperties.setDistanceMetric("cosine");

        qaReadinessCache = mock(Cache.class);
        qaRetrievalCache = mock(Cache.class);
        documentChunksCache = mock(Cache.class);
        lenient().when(cacheManager.getCache(CacheNames.QA_READINESS)).thenReturn(qaReadinessCache);
        lenient().when(cacheManager.getCache(CacheNames.QA_RETRIEVAL)).thenReturn(qaRetrievalCache);
        lenient().when(cacheManager.getCache(CacheNames.DOCUMENT_CHUNKS)).thenReturn(documentChunksCache);

        service = new EmbeddingRebuildService(
                snowflakeIdGenerator,
                embeddingProperties,
                embeddingConfigurationStateService,
                embeddingRebuildRunRepository,
                documentChunkRepository,
                documentRepository,
                documentEmbeddingService,
                indexingTaskRepository,
                knowledgeBaseRepository,
                indexingExecutor,
                cacheManager
        );
    }

    @Test
    void dispatchRunShouldMarkRunFailedWhenExecutorRejectsTask() {
        EmbeddingRebuildRunEntity run = new EmbeddingRebuildRunEntity();
        run.setId(1001L);
        run.setStatus(EmbeddingRebuildRunStatus.QUEUED);
        run.setTotalDocumentCount(4);

        when(embeddingRebuildRunRepository.findById(1001L)).thenReturn(Optional.of(run));
        doThrow(new RejectedExecutionException("queue full")).when(indexingExecutor).execute(any(Runnable.class));

        ReflectionTestUtils.invokeMethod(service, "dispatchRun", 1001L);

        assertThat(run.getStatus()).isEqualTo(EmbeddingRebuildRunStatus.FAILED);
        assertThat(run.getErrorSummary()).contains("Failed to dispatch embedding rebuild");
        assertThat(run.getFailedDocumentCount()).isEqualTo(4);
        verify(embeddingConfigurationStateService).markRebuildFailed(1001L);
        verify(embeddingRebuildRunRepository).updateById(run);
        verify(qaReadinessCache).clear();
        verify(qaRetrievalCache).clear();
        verify(documentChunksCache).clear();
    }

    @Test
    void recoverQueuedRebuildRunsShouldRedispatchQueuedRuns() {
        EmbeddingRebuildRunEntity queuedRun = new EmbeddingRebuildRunEntity();
        queuedRun.setId(2002L);
        queuedRun.setStatus(EmbeddingRebuildRunStatus.QUEUED);

        when(embeddingRebuildRunRepository.findByStatuses(
                List.of(EmbeddingRebuildRunStatus.QUEUED),
                10
        )).thenReturn(List.of(queuedRun));

        service.recoverQueuedRebuildRuns();

        verify(indexingExecutor).execute(any(Runnable.class));
        verify(embeddingConfigurationStateService, never()).markRebuildFailed(any());
        verify(embeddingRebuildRunRepository, never()).updateById(eq(queuedRun));
    }
}
