package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.integration.llm.OpenAiCompatibleClient;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentEmbeddingResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 文档 chunk 向量化服务。
 *
 * Day 9 在已有 chunk 数据基础上补齐 embedding 写库链路。
 */
@Service
public class DocumentEmbeddingService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;
    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    public DocumentEmbeddingService(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentRepository documentRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    RagEmbeddingProperties ragEmbeddingProperties,
                                    OpenAiCompatibleClient openAiCompatibleClient) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    /** 对指定文档的 chunk 执行向量化并写入 pgvector。 */
    @Transactional
    public DocumentEmbeddingResponse embed(String kbCode, String documentCode) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);

        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        if (document.getStatus() != DocumentStatus.INDEXED) {
            throw new BusinessException("Document must be INDEXED before embedding: " + documentCode);
        }

        int batchSize = normalizeBatchSize(ragEmbeddingProperties.getBatchSize());
        int embeddedCount = 0;
        int failedCount = 0;
        log.info(StructuredLogMessage.of("document.embedding.started")
                .field("kbCode", kbCode)
                .field("documentCode", documentCode)
                .field("embeddingModel", ragEmbeddingProperties.getModel())
                .field("batchSize", batchSize)
                .build());

        while (true) {
            List<DocumentChunkEntity> chunks = documentChunkRepository.findEmbeddableChunksByDocumentId(
                    document.getId(),
                    List.of(EmbeddingStatus.PENDING, EmbeddingStatus.FAILED),
                    batchSize
            );
            if (chunks.isEmpty()) {
                break;
            }

            OffsetDateTime startedAt = OffsetDateTime.now();
            log.info(StructuredLogMessage.of("document.embedding.batch_started")
                    .field("kbCode", kbCode)
                    .field("documentCode", documentCode)
                    .field("batchChunkCount", chunks.size())
                    .build());
            for (DocumentChunkEntity chunk : chunks) {
                documentChunkRepository.updateEmbeddingState(
                        chunk.getId(),
                        EmbeddingStatus.EMBEDDING,
                        ragEmbeddingProperties.getModel(),
                        null,
                        startedAt
                );
            }

            try {
                List<List<Double>> embeddings = openAiCompatibleClient.createEmbeddings(
                        ragEmbeddingProperties.getBaseUrl(),
                        ragEmbeddingProperties.getApiKey(),
                        ragEmbeddingProperties.getEmbeddingPath(),
                        ragEmbeddingProperties.getModel(),
                        chunks.stream().map(DocumentChunkEntity::getContent).toList()
                );
                if (embeddings.size() != chunks.size()) {
                    throw new BusinessException("Embedding result size does not match chunk count");
                }

                OffsetDateTime updatedAt = OffsetDateTime.now();
                for (int index = 0; index < chunks.size(); index++) {
                    DocumentChunkEntity chunk = chunks.get(index);
                    String vectorLiteral = toVectorLiteral(embeddings.get(index));
                    documentChunkRepository.updateEmbeddingVector(
                            chunk.getId(),
                            EmbeddingStatus.EMBEDDED,
                            ragEmbeddingProperties.getModel(),
                            vectorLiteral,
                            updatedAt
                    );
                    embeddedCount++;
                }
                log.info(StructuredLogMessage.of("document.embedding.batch_succeeded")
                        .field("kbCode", kbCode)
                        .field("documentCode", documentCode)
                        .field("batchChunkCount", chunks.size())
                        .field("embeddedCount", embeddedCount)
                        .build());
            } catch (RuntimeException ex) {
                OffsetDateTime failedAt = OffsetDateTime.now();
                String errorMessage = truncate(ex.getMessage());
                for (DocumentChunkEntity chunk : chunks) {
                    documentChunkRepository.updateEmbeddingState(
                            chunk.getId(),
                            EmbeddingStatus.FAILED,
                            ragEmbeddingProperties.getModel(),
                            errorMessage,
                            failedAt
                    );
                    failedCount++;
                }
                log.warn(StructuredLogMessage.of("document.embedding.batch_failed")
                        .field("kbCode", kbCode)
                        .field("documentCode", documentCode)
                        .field("batchChunkCount", chunks.size())
                        .field("message", errorMessage)
                        .build());
                throw ex;
            }
        }

        long totalEmbeddedChunkCount = documentChunkRepository.countByDocumentIdAndEmbeddingStatus(
                document.getId(),
                EmbeddingStatus.EMBEDDED
        );
        DocumentEmbeddingResponse response = new DocumentEmbeddingResponse(
                document.getId(),
                document.getDocumentCode(),
                kbCode,
                ragEmbeddingProperties.getModel(),
                ragEmbeddingProperties.getVectorDimensions(),
                batchSize,
                embeddedCount,
                failedCount,
                totalEmbeddedChunkCount,
                OffsetDateTime.now()
        );
        log.info(StructuredLogMessage.of("document.embedding.completed")
                .field("kbCode", kbCode)
                .field("documentCode", documentCode)
                .field("embeddedChunkCount", response.embeddedChunkCount())
                .field("failedChunkCount", response.failedChunkCount())
                .field("totalEmbeddedChunkCount", response.totalEmbeddedChunkCount())
                .build());
        return response;
    }

    private void ensureKnowledgeBaseActive(KnowledgeBaseEntity knowledgeBase) {
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BusinessException("Knowledge base is inactive: " + knowledgeBase.getKbCode());
        }
    }

    private int normalizeBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize < 1) {
            return 16;
        }
        return batchSize;
    }

    private String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new BusinessException("Embedding vector must not be empty");
        }
        return "[" + vector.stream()
                .map(value -> String.format(Locale.ROOT, "%.12f", value))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]";
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown embedding error";
        }
        if (message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
