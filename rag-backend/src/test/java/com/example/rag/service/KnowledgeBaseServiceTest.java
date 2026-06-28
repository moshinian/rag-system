package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.ingestion.storage.LocalFileStorageService;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.KnowledgeBaseEnableResponse;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.persistence.ChatMessageRepository;
import com.example.rag.persistence.ChatSessionRepository;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识库管理服务测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private IndexingTaskRepository indexingTaskRepository;

    @Mock
    private com.example.rag.common.id.SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private LocalFileStorageService localFileStorageService;

    @Mock
    private DocumentIndexingService documentIndexingService;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void listShouldReturnPagedKnowledgeBases() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setName("Settlement KB");
        entity.setDescription("desc");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);
        entity.setCreatedBy("tester");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.page(any()))
                .thenReturn(new PageResult<>(List.of(entity), 1, 1, 20));

        PageResponse<KnowledgeBaseResponse> response = knowledgeBaseService.list("active", 1L, 20L);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement()
                .extracting(responseItem -> responseItem.kbCode(), responseItem -> responseItem.status())
                .containsExactly("settlement-kb", "ACTIVE");
    }

    @Test
    void getShouldReturnKnowledgeBaseDetail() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setName("Settlement KB");
        entity.setDescription("desc");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);
        entity.setCreatedBy("tester");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));

        KnowledgeBaseResponse response = knowledgeBaseService.get("settlement-kb");

        assertThat(response.kbCode()).isEqualTo("settlement-kb");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void disableShouldUpdateKnowledgeBaseStatus() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(indexingTaskRepository.existsActiveTaskInKnowledgeBase(1L, "DOCUMENT_INDEXING")).thenReturn(false);
        when(knowledgeBaseRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseResponse response = knowledgeBaseService.disable("settlement-kb");

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void disableShouldRejectWhenKnowledgeBaseHasActiveIndexingTasks() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(indexingTaskRepository.existsActiveTaskInKnowledgeBase(1L, "DOCUMENT_INDEXING")).thenReturn(true);

        assertThatThrownBy(() -> knowledgeBaseService.disable("settlement-kb"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active indexing tasks");
    }

    @Test
    void enableShouldUpdateKnowledgeBaseStatus() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.INACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseEnableResponse response = knowledgeBaseService.enable("settlement-kb", false, null);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.retriedFailedTaskCount()).isZero();
    }

    @Test
    void enableShouldOptionallyRetryFailedIndexingTasks() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.INACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentIndexingService.retryLatestFailedTasksInKnowledgeBase("settlement-kb", "frontend-dashboard"))
                .thenReturn(new DocumentIndexingService.BatchRetryIndexingResult(
                        2,
                        1,
                        0,
                        1,
                        List.of("DOC-1", "DOC-2")
                ));

        KnowledgeBaseEnableResponse response = knowledgeBaseService.enable(
                "settlement-kb",
                true,
                "frontend-dashboard"
        );

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.retryFailedIndexingTasks()).isTrue();
        assertThat(response.retriedFailedTaskCount()).isEqualTo(2);
        assertThat(response.retriedDocumentCodes()).containsExactly("DOC-1", "DOC-2");
    }

    @Test
    void deleteShouldCascadeDeleteKnowledgeBaseMaterialsAndData() throws Exception {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setName("Settlement KB");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);
        entity.setCreatedBy("tester");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        DocumentEntity document = new DocumentEntity();
        document.setStoragePath("/tmp/settlement-kb/DOC-1_plan.md");

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(indexingTaskRepository.existsActiveTaskInKnowledgeBase(1L, "DOCUMENT_INDEXING")).thenReturn(false);
        when(documentRepository.findByKnowledgeBaseId(1L)).thenReturn(List.of(document));
        when(chatSessionRepository.findIdsByKnowledgeBaseId(1L)).thenReturn(List.of(11L, 12L));

        KnowledgeBaseResponse response = knowledgeBaseService.delete("settlement-kb");

        assertThat(response.kbCode()).isEqualTo("settlement-kb");
        verify(chatMessageRepository).deleteBySessionIds(eq(List.of(11L, 12L)));
        verify(chatSessionRepository).deleteByKnowledgeBaseId(1L);
        verify(indexingTaskRepository).deleteByKnowledgeBaseId(1L);
        verify(documentChunkRepository).deleteByKnowledgeBaseId(1L);
        verify(documentRepository).deleteByKnowledgeBaseId(1L);
        verify(knowledgeBaseRepository).deleteById(1L);
        verify(localFileStorageService).deleteKnowledgeBaseDirectory("settlement-kb");
    }

    @Test
    void deleteShouldRejectWhenKnowledgeBaseHasActiveIndexingTasks() throws Exception {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(indexingTaskRepository.existsActiveTaskInKnowledgeBase(1L, "DOCUMENT_INDEXING")).thenReturn(true);

        assertThatThrownBy(() -> knowledgeBaseService.delete("settlement-kb"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deleted");

        verify(documentRepository, never()).deleteByKnowledgeBaseId(any());
        verify(localFileStorageService, never()).deleteKnowledgeBaseDirectory(any());
    }
}
