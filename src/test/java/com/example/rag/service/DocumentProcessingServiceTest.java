package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RagChunkingProperties;
import com.example.rag.ingestion.chunk.FixedWindowChunker;
import com.example.rag.ingestion.parser.MarkdownDocumentTextParser;
import com.example.rag.ingestion.parser.PlainTextDocumentTextParser;
import com.example.rag.ingestion.parser.PdfDocumentTextParser;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
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
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private IndexingTaskRepository indexingTaskRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Captor
    private ArgumentCaptor<List<DocumentChunkEntity>> documentChunkCaptor;

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

        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb"))
                .thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(createKnowledgeBase()));
        when(documentRepository.updateById(any())).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            entity.setUpdatedAt(OffsetDateTime.now());
            return entity;
        });
        when(indexingTaskRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockSnowflakeSequence(10L);
        when(documentChunkRepository.batchInsert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProcessingService service = new DocumentProcessingService(
                documentRepository,
                documentChunkRepository,
                indexingTaskRepository,
                knowledgeBaseRepository,
                List.of(new MarkdownDocumentTextParser(), new PlainTextDocumentTextParser()),
                new FixedWindowChunker(defaultChunkingProperties()),
                snowflakeIdGenerator,
                new ObjectMapper()
        );

        DocumentProcessResponse response = service.process("settlement-kb", "DOC-1", "tester");

        assertThat(response.status()).isEqualTo("INDEXED");
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(response.parserName()).isEqualTo("markdown");

        verify(documentChunkRepository).batchInsert(documentChunkCaptor.capture());
        assertThat(documentChunkCaptor.getValue()).isNotEmpty();
        assertThat(documentChunkCaptor.getValue().get(0).getTitle()).isEqualTo("Settlement Overview");
        assertThat(documentChunkCaptor.getValue().get(0).getMetadataJson()).contains("\"parser\":\"markdown\"");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        ArgumentCaptor<IndexingTaskEntity> taskCaptor = ArgumentCaptor.forClass(IndexingTaskEntity.class);
        verify(indexingTaskRepository).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getChunkCount()).isGreaterThan(0);
        assertThat(taskCaptor.getValue().getParserName()).isEqualTo("markdown");
    }

    @Test
    void processShouldParsePdfAndPersistChunks() throws Exception {
        DocumentEntity document = createDocument("pdf");
        Path file = tempDir.resolve("sample.pdf");
        Files.copy(Path.of("work/samples/day4-upload-sample.pdf"), file);
        document.setStoragePath(file.toString());

        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb"))
                .thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(createKnowledgeBase()));
        when(documentRepository.updateById(any())).thenAnswer(invocation -> {
            DocumentEntity entity = invocation.getArgument(0);
            entity.setUpdatedAt(OffsetDateTime.now());
            return entity;
        });
        when(indexingTaskRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(indexingTaskRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockSnowflakeSequence(10L);
        when(documentChunkRepository.batchInsert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProcessingService service = new DocumentProcessingService(
                documentRepository,
                documentChunkRepository,
                indexingTaskRepository,
                knowledgeBaseRepository,
                List.of(new MarkdownDocumentTextParser(), new PlainTextDocumentTextParser(), new PdfDocumentTextParser()),
                new FixedWindowChunker(defaultChunkingProperties()),
                snowflakeIdGenerator,
                new ObjectMapper()
        );

        DocumentProcessResponse response = service.process("settlement-kb", "DOC-1", "tester");

        assertThat(response.status()).isEqualTo("INDEXED");
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(response.parserName()).isEqualTo("pdfbox");
    }

    @Test
    void processShouldRejectWhenKnowledgeBaseInactive() {
        KnowledgeBaseEntity knowledgeBase = createKnowledgeBase();
        knowledgeBase.setStatus(KnowledgeBaseStatus.INACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));

        DocumentProcessingService service = new DocumentProcessingService(
                documentRepository,
                documentChunkRepository,
                indexingTaskRepository,
                knowledgeBaseRepository,
                List.of(new MarkdownDocumentTextParser(), new PlainTextDocumentTextParser()),
                new FixedWindowChunker(defaultChunkingProperties()),
                snowflakeIdGenerator,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> service.process("settlement-kb", "DOC-1", "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Knowledge base is inactive");
    }

    /**
     * 构造一份最小文档实体，供不同测试复用。
     */
    private DocumentEntity createDocument(String fileType) {
        DocumentEntity document = new DocumentEntity();
        document.setId(1L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setFileName("sample." + fileType);
        document.setDisplayName("Sample Document");
        document.setFileType(fileType);
        document.setMediaType("text/" + fileType);
        document.setContentHash("hash");
        document.setStatus(DocumentStatus.UPLOADED);
        return document;
    }

    private KnowledgeBaseEntity createKnowledgeBase() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setName("Settlement");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        return knowledgeBase;
    }

    private void mockSnowflakeSequence(long startValue) {
        AtomicLong sequence = new AtomicLong(startValue);
        when(snowflakeIdGenerator.nextId()).thenAnswer(invocation -> sequence.getAndIncrement());
    }

    private RagChunkingProperties defaultChunkingProperties() {
        return new RagChunkingProperties();
    }
}
