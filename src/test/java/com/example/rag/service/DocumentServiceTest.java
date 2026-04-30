package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.ingestion.storage.LocalFileStorageService;
import com.example.rag.model.entity.DocumentEntity;
import com.example.rag.model.entity.KnowledgeBaseEntity;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.repository.KnowledgeBaseRepository;
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

        when(knowledgeBaseRepository.findByKbCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.existsByKnowledgeBaseIdAndContentHash(eq(100L), any())).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(localFileStorageService.store(any(), any(), any(), any(), any()))
                .thenReturn(Path.of("data/uploads/settlement-kb/20260430/DOC-123456789_plan.md"));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
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
        verify(documentRepository).save(captor.capture());
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

        when(knowledgeBaseRepository.findByKbCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.existsByKnowledgeBaseIdAndContentHash(eq(100L), any())).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(999L);
        when(localFileStorageService.store(any(), any(), any(), any(), any()))
                .thenReturn(Path.of("data/uploads/settlement-kb/20260430/DOC-999_notes.txt"));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
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

        when(knowledgeBaseRepository.findByKbCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));

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
}
