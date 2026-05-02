package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.ingestion.storage.LocalFileStorageService;
import com.example.rag.model.response.DocumentChunkResponse;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.response.DocumentDetailResponse;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentSummaryResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档上传服务测试。
 *
 * 主要验证媒体类型回退、content-type 归一化以及非法文件类型拦截。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private LocalFileStorageService localFileStorageService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private DocumentService documentService;

    private KnowledgeBaseEntity knowledgeBase;

    @BeforeEach
    void setUp() {
        // 构造一个最小知识库上下文，供上传服务复用。
        knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setName("Settlement KB");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        knowledgeBase.setCreatedBy("tester");
    }

    @Test
    void uploadShouldFallbackMediaTypeFromFileExtension() throws Exception {
        // 当 multipart 没有 content-type 时，应当根据扩展名回退。
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "plan.md",
                null,
                "# title".getBytes()
        );

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.existsInKnowledgeBaseByContentHash(eq(100L), any())).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(localFileStorageService.store(any(), any(), any(), any(), any()))
                .thenReturn(Path.of("data/uploads/settlement-kb/20260430/DOC-123456789_plan.md"));
        when(documentRepository.insert(any())).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            OffsetDateTime now = OffsetDateTime.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return entity;
        });

        DocumentUploadResponse response = documentService.upload(
                "settlement-kb",
                file,
                "Plan",
                "plan",
                "work",
                "codex"
        );

        assertThat(response.mediaType()).isEqualTo("text/markdown");

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).insert(captor.capture());
        assertThat(captor.getValue().getMediaType()).isEqualTo("text/markdown");
    }

    @Test
    void uploadShouldNormalizeMultipartContentType() throws Exception {
        // 即使客户端传了带参数的 content-type，也应当归一化成标准媒体类型。
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "Text/Plain; charset=UTF-8",
                "hello".getBytes()
        );

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.existsInKnowledgeBaseByContentHash(eq(100L), any())).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(999L);
        when(localFileStorageService.store(any(), any(), any(), any(), any()))
                .thenReturn(Path.of("data/uploads/settlement-kb/20260430/DOC-999_notes.txt"));
        when(documentRepository.insert(any())).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            OffsetDateTime now = OffsetDateTime.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return entity;
        });

        DocumentUploadResponse response = documentService.upload(
                "settlement-kb",
                file,
                null,
                null,
                null,
                null
        );

        assertThat(response.mediaType()).isEqualTo("text/plain");
    }

    @Test
    void uploadShouldRejectUnsupportedFileType() {
        // 当前阶段只允许 md / txt / pdf，其他类型要直接拒绝。
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "bad".getBytes()
        );

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));

        assertThatThrownBy(() -> documentService.upload(
                "settlement-kb",
                file,
                null,
                null,
                null,
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void listDocumentsShouldReturnPagedResults() {
        DocumentEntity document = new DocumentEntity();
        document.setId(1L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setFileName("plan.md");
        document.setDisplayName("Plan");
        document.setFileType("md");
        document.setMediaType("text/markdown");
        document.setFileSize(128L);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCreatedBy("tester");
        document.setCreatedAt(OffsetDateTime.now());
        document.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.pageByKnowledgeBase(any()))
                .thenReturn(new PageResult<>(List.of(document), 1, 1, 20));

        PageResponse<DocumentSummaryResponse> response = documentService.listDocuments(
                "settlement-kb",
                "uploaded",
                1L,
                20L
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement()
                .extracting(DocumentSummaryResponse::documentCode, DocumentSummaryResponse::knowledgeBaseCode)
                .containsExactly("DOC-1", "settlement-kb");
    }

    @Test
    void getDocumentShouldReturnDetail() {
        DocumentEntity document = new DocumentEntity();
        document.setId(1L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setFileName("plan.md");
        document.setDisplayName("Plan");
        document.setFileType("md");
        document.setMediaType("text/markdown");
        document.setStoragePath("data/uploads/plan.md");
        document.setFileSize(128L);
        document.setContentHash("hash");
        document.setStatus(DocumentStatus.UPLOADED);
        document.setVersion(1);
        document.setSource("work");
        document.setTags("plan");
        document.setCreatedBy("tester");
        document.setCreatedAt(OffsetDateTime.now());
        document.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));

        DocumentDetailResponse response = documentService.getDocument("settlement-kb", "DOC-1");

        assertThat(response.documentCode()).isEqualTo("DOC-1");
        assertThat(response.knowledgeBaseCode()).isEqualTo("settlement-kb");
        assertThat(response.storagePath()).isEqualTo("data/uploads/plan.md");
    }

    @Test
    void disableDocumentShouldUpdateStatus() {
        DocumentEntity document = new DocumentEntity();
        document.setId(1L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.UPLOADED);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(documentRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentDetailResponse response = documentService.disableDocument("settlement-kb", "DOC-1");

        assertThat(response.status()).isEqualTo("DISABLED");
    }

    @Test
    void uploadShouldRejectWhenKnowledgeBaseInactive() {
        knowledgeBase.setStatus(KnowledgeBaseStatus.INACTIVE);
        MockMultipartFile file = new MockMultipartFile("file", "plan.md", null, "# title".getBytes());

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));

        assertThatThrownBy(() -> documentService.upload(
                "settlement-kb",
                file,
                null,
                null,
                null,
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Knowledge base is inactive");
    }

    @Test
    void listDocumentChunksShouldReturnOrderedChunks() {
        DocumentEntity document = new DocumentEntity();
        document.setId(1L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");

        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(11L);
        chunk.setDocumentId(1L);
        chunk.setChunkIndex(0);
        chunk.setChunkType("TEXT");
        chunk.setTitle("Intro");
        chunk.setContent("hello");
        chunk.setContentLength(5);
        chunk.setTokenCount(1);
        chunk.setStartOffset(0);
        chunk.setEndOffset(5);
        chunk.setMetadataJson("{\"parser\":\"markdown\"}");
        chunk.setStatus(com.example.rag.model.enums.DocumentChunkStatus.ACTIVE);
        chunk.setCreatedAt(OffsetDateTime.now());
        chunk.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(1L)).thenReturn(List.of(chunk));

        List<DocumentChunkResponse> response = documentService.listDocumentChunks("settlement-kb", "DOC-1");

        assertThat(response).singleElement()
                .extracting(DocumentChunkResponse::chunkIndex, DocumentChunkResponse::title, DocumentChunkResponse::status)
                .containsExactly(0, "Intro", "ACTIVE");
    }
}
