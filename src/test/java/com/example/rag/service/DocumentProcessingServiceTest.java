package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.ingestion.chunk.FixedWindowChunker;
import com.example.rag.ingestion.parser.MarkdownDocumentTextParser;
import com.example.rag.ingestion.parser.PlainTextDocumentTextParser;
import com.example.rag.model.entity.DocumentChunkEntity;
import com.example.rag.model.entity.DocumentEntity;
import com.example.rag.model.entity.KnowledgeBaseEntity;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档处理服务单元测试。
 *
 * 不依赖真实数据库，重点验证主流程分支是否正确。
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @TempDir
    Path tempDir;

    @Test
    void processShouldParseMarkdownAndPersistChunks() throws Exception {
        // 使用临时 markdown 文件模拟真实存储文件。
        DocumentEntity document = createDocument("md");
        Path file = tempDir.resolve("sample.md");
        Files.writeString(file, """
                # Settlement Overview

                This is a markdown sample for chunking.

                ## Billing
                Billing processing should be stable and observable.

                ## Reconciliation
                Reconciliation logic should support retries and traceable results.
                """);
        document.setStoragePath(file.toString());

        when(documentRepository.findByDocumentCodeAndKnowledgeBase_KbCode("DOC-1", "settlement-kb"))
                .thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            entity.setUpdatedAt(OffsetDateTime.now());
            return entity;
        });
        when(snowflakeIdGenerator.nextId()).thenReturn(11L, 12L, 13L, 14L, 15L);
        when(documentChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProcessingService service = new DocumentProcessingService(
                documentRepository,
                documentChunkRepository,
                List.of(new MarkdownDocumentTextParser(), new PlainTextDocumentTextParser()),
                new FixedWindowChunker(),
                snowflakeIdGenerator,
                new ObjectMapper()
        );

        DocumentProcessResponse response = service.process("settlement-kb", "DOC-1", "tester");

        assertThat(response.status()).isEqualTo("INDEXED");
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(response.parserName()).isEqualTo("markdown");

        ArgumentCaptor<List<DocumentChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(documentChunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
        assertThat(captor.getValue().get(0).getTitle()).isEqualTo("Settlement Overview");
        assertThat(captor.getValue().get(0).getMetadataJson()).contains("\"parser\":\"markdown\"");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
    }

    @Test
    void processShouldMarkDocumentFailedWhenParserUnavailable() throws Exception {
        // 当前还没支持 PDF 解析，所以应该明确进入 FAILED。
        DocumentEntity document = createDocument("pdf");
        Path file = tempDir.resolve("sample.pdf");
        Files.writeString(file, "fake");
        document.setStoragePath(file.toString());

        when(documentRepository.findByDocumentCodeAndKnowledgeBase_KbCode("DOC-1", "settlement-kb"))
                .thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            entity.setUpdatedAt(OffsetDateTime.now());
            return entity;
        });

        DocumentProcessingService service = new DocumentProcessingService(
                documentRepository,
                documentChunkRepository,
                List.of(new MarkdownDocumentTextParser(), new PlainTextDocumentTextParser()),
                new FixedWindowChunker(),
                snowflakeIdGenerator,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> service.process("settlement-kb", "DOC-1", "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No parser available");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    /**
     * 构造一份最小文档实体，供不同测试复用。
     */
    private DocumentEntity createDocument(String fileType) {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setName("Settlement");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(1L);
        document.setKnowledgeBase(knowledgeBase);
        document.setDocumentCode("DOC-1");
        document.setFileName("sample." + fileType);
        document.setDisplayName("Sample Document");
        document.setFileType(fileType);
        document.setMediaType("text/" + fileType);
        document.setContentHash("hash");
        document.setStatus(DocumentStatus.UPLOADED);
        return document;
    }
}
