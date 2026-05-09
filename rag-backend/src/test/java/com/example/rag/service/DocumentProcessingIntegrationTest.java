package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档处理集成测试。
 *
 * 该测试会真实连接 PostgreSQL，验证 chunk 能够成功写入数据库。
 */
@SpringBootTest
@Transactional
class DocumentProcessingIntegrationTest {

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private IndexingTaskRepository indexingTaskRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @TempDir
    Path tempDir;

    @Test
    void processShouldPersistChunksToPostgreSql() throws Exception {
        // 为了避免和已有本地数据冲突，测试里动态生成知识库和文档编码。
        long knowledgeBaseId = snowflakeIdGenerator.nextId();
        long documentId = snowflakeIdGenerator.nextId();
        String kbCode = "itest-kb-" + knowledgeBaseId;
        String documentCode = "DOC-" + documentId;

        // 准备一份真实 markdown 文件，模拟本地存储中的上传文档。
        Path markdownFile = tempDir.resolve("integration.md");
        Files.writeString(markdownFile, """
                # Integration Title

                This integration test verifies that parsed chunks are persisted to PostgreSQL.

                ## Section One
                The processing service should read markdown content and create chunk rows.

                ## Section Two
                Each generated chunk should keep metadata, offsets, and status information.
                """);

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setName("Integration KB");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        knowledgeBase.setCreatedBy("itest");
        knowledgeBaseRepository.insert(knowledgeBase);

        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setDocumentCode(documentCode);
        document.setFileName("integration.md");
        document.setDisplayName("Integration Document");
        document.setFileType("md");
        document.setMediaType("text/markdown");
        document.setStoragePath(markdownFile.toString());
        document.setFileSize(Files.size(markdownFile));
        document.setContentHash("integration-hash-" + documentId);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setVersion(1);
        document.setCreatedBy("itest");
        documentRepository.insert(document);

        // 执行真实主链路处理。
        DocumentProcessResponse response = documentProcessingService.process(kbCode, documentCode, "itest");

        assertThat(response.status()).isEqualTo("INDEXED");
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId)).isNotEmpty();
        assertThat(documentRepository.findByCode(documentCode))
                .get()
                .extracting(DocumentEntity::getStatus)
                .isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexingTaskRepository.findByDocumentIdOrderByCreatedAtDesc(documentId))
                .extracting(IndexingTaskEntity::getChunkCount)
                .first()
                .isEqualTo(response.chunkCount());
    }
}
