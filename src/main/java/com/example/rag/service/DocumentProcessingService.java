package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.ingestion.chunk.ChunkDraft;
import com.example.rag.ingestion.chunk.FixedWindowChunker;
import com.example.rag.ingestion.parser.DocumentTextParser;
import com.example.rag.ingestion.parser.ParsedDocument;
import com.example.rag.model.entity.DocumentChunkEntity;
import com.example.rag.model.entity.DocumentEntity;
import com.example.rag.model.enums.DocumentChunkStatus;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档处理服务。
 *
 * 负责读取已上传文档，执行解析、切块和 chunk 入库，
 * 并推进文档处理状态。
 */
@Service
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final List<DocumentTextParser> documentTextParsers;
    private final FixedWindowChunker fixedWindowChunker;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                     DocumentChunkRepository documentChunkRepository,
                                     List<DocumentTextParser> documentTextParsers,
                                     FixedWindowChunker fixedWindowChunker,
                                     SnowflakeIdGenerator snowflakeIdGenerator,
                                     ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentTextParsers = documentTextParsers;
        this.fixedWindowChunker = fixedWindowChunker;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理指定文档。
     *
     * 流程包括读取文档记录、推进处理状态、解析文本、执行切块、
     * 写入 document_chunk，并在结束时更新最终状态。
     */
    public DocumentProcessResponse process(String kbCode, String documentCode, String operator) {
        DocumentEntity document = documentRepository.findByDocumentCodeAndKnowledgeBase_KbCode(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));

        // 被禁用的文档不允许再进入处理链路。
        if (document.getStatus() == DocumentStatus.DISABLED) {
            throw new BusinessException("Document is disabled and cannot be processed: " + documentCode);
        }

        try {
            // 先进入 PARSING，便于观察当前处理阶段。
            updateStatus(document, DocumentStatus.PARSING, null);
            ParsedDocument parsedDocument = parse(document);
            if (parsedDocument.sections().isEmpty()) {
                throw new BusinessException("No readable text extracted from document: " + documentCode);
            }

            updateStatus(document, DocumentStatus.PARSED, null);
            List<ChunkDraft> chunkDrafts = fixedWindowChunker.chunk(parsedDocument);
            if (chunkDrafts.isEmpty()) {
                throw new BusinessException("No chunks generated from document: " + documentCode);
            }

            updateStatus(document, DocumentStatus.CHUNKING, null);
            // 重新处理同一文档时，先清掉旧 chunk，避免重复数据残留。
            documentChunkRepository.deleteByDocument_Id(document.getId());
            List<DocumentChunkEntity> chunks = chunkDrafts.stream()
                    .map(draft -> toChunkEntity(document, draft, parsedDocument.parserName()))
                    .toList();
            documentChunkRepository.saveAll(chunks);

            updateStatus(document, DocumentStatus.INDEXED, null);
            return new DocumentProcessResponse(
                    document.getId(),
                    document.getDocumentCode(),
                    document.getKnowledgeBase().getKbCode(),
                    document.getFileType(),
                    document.getStatus().name(),
                    chunks.size(),
                    parsedDocument.parserName(),
                    document.getUpdatedAt()
            );
        } catch (RuntimeException ex) {
            // 任一阶段失败都统一落到 FAILED，方便后续排障和重试。
            markFailed(document, ex.getMessage());
            throw ex;
        }
    }

    /** 根据文件类型选择解析器，并读取原始文件。 */
    private ParsedDocument parse(DocumentEntity document) {
        DocumentTextParser parser = documentTextParsers.stream()
                .filter(candidate -> candidate.supports(document.getFileType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No parser available for file type: " + document.getFileType()));

        Path path = Path.of(document.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException("Stored file not found: " + document.getStoragePath());
        }

        try {
            return parser.parse(document, path);
        } catch (IOException ex) {
            throw new BusinessException("Failed to parse document: " + ex.getMessage());
        }
    }

    /** 把切块结果转换成数据库实体。 */
    private DocumentChunkEntity toChunkEntity(DocumentEntity document, ChunkDraft draft, String parserName) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setKnowledgeBase(document.getKnowledgeBase());
        entity.setDocument(document);
        entity.setChunkIndex(draft.chunkIndex());
        entity.setChunkType("TEXT");
        entity.setTitle(normalizeTitle(draft.title(), document));
        entity.setContent(draft.content());
        entity.setContentLength(draft.content().length());
        entity.setTokenCount(estimateTokenCount(draft.content()));
        entity.setStartOffset(draft.startOffset());
        entity.setEndOffset(draft.endOffset());
        entity.setMetadataJson(buildMetadataJson(document, draft, parserName));
        entity.setStatus(DocumentChunkStatus.ACTIVE);
        return entity;
    }

    /** 如果 chunk 没有独立标题，则退回到文档展示名。 */
    private String normalizeTitle(String title, DocumentEntity document) {
        if (title == null || title.isBlank()) {
            return document.getDisplayName();
        }
        return title;
    }

    /**
     * 估算 chunk 的 token 数量。
     *
     * 当前实现采用粗粒度估算，主要用于观察 chunk 规模。
     */
    private int estimateTokenCount(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split("\\s+").length;
    }

    /** 构建 chunk 级元数据。 */
    private String buildMetadataJson(DocumentEntity document, ChunkDraft draft, String parserName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("parser", parserName);
        metadata.put("fileType", document.getFileType());
        metadata.put("mediaType", document.getMediaType());
        metadata.put("documentCode", document.getDocumentCode());
        metadata.put("chunkStrategy", "fixed-window");
        metadata.put("overlapChars", 80);
        metadata.put("sectionTitle", normalizeTitle(draft.title(), document));
        metadata.put("contentHash", document.getContentHash());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize chunk metadata: " + ex.getMessage());
        }
    }

    /** 更新文档当前状态。 */
    private void updateStatus(DocumentEntity document, DocumentStatus status, String errorMessage) {
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        documentRepository.save(document);
    }

    /** 在处理失败时记录错误原因。 */
    private void markFailed(DocumentEntity document, String message) {
        document.setStatus(DocumentStatus.FAILED);
        document.setErrorMessage(truncate(message));
        documentRepository.save(document);
    }

    /** 截断错误信息，避免超过数据库字段长度。 */
    private String truncate(String message) {
        if (message == null) {
            return "Unknown processing error";
        }
        if (message.length() <= 1024) {
            return message;
        }
        return message.substring(0, 1024);
    }
}
