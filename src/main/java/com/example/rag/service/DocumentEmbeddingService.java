package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.CacheNames;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.integration.llm.OpenAiCompatibleClient;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentEmbeddingResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 文档向量化服务。
 *
 * 负责读取已切块的文档内容，调用 embedding 服务生成向量，并把结果写回 `document_chunk`。
 */
@Service
public class DocumentEmbeddingService {

    private static final String TASK_TYPE_DOCUMENT_INDEXING = "DOCUMENT_INDEXING";
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;
    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    public DocumentEmbeddingService(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentRepository documentRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    IndexingTaskRepository indexingTaskRepository,
                                    RagEmbeddingProperties ragEmbeddingProperties,
                                    OpenAiCompatibleClient openAiCompatibleClient) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.indexingTaskRepository = indexingTaskRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    /** 对指定文档的 chunk 执行向量化并写入 pgvector。 */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DOCUMENT_CHUNKS, key = "#kbCode + ':' + #documentCode"),
            @CacheEvict(cacheNames = CacheNames.QA_READINESS, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true)
    })
    public DocumentEmbeddingResponse embed(String kbCode, String documentCode) {
        return embedInternal(kbCode, documentCode, false);
    }

    /** 异步索引链路内部调用时允许复用 embed 逻辑，但要绕过“活动索引任务”自校验。 */
    DocumentEmbeddingResponse embedForIndexing(String kbCode, String documentCode) {
        return embedInternal(kbCode, documentCode, true);
    }

    private DocumentEmbeddingResponse embedInternal(String kbCode, String documentCode, boolean allowDuringActiveIndexing) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);

        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        if (document.getStatus() != DocumentStatus.INDEXED) {
            throw new BusinessException("Document must be INDEXED before embedding: " + documentCode);
        }
        if (!allowDuringActiveIndexing
                && indexingTaskRepository.existsActiveTask(document.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("Document has an active indexing task and cannot be manually embedded: "
                    + documentCode);
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
            // 先把整批 chunk 标记为 EMBEDDING，便于外部观察当前进度。
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
                // 接口返回顺序和输入顺序一一对应，逐条回写到原始 chunk 即可。
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

    /** 向量化前要求知识库仍然处于启用状态。 */
    private void ensureKnowledgeBaseActive(KnowledgeBaseEntity knowledgeBase) {
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BusinessException("Knowledge base is inactive: " + knowledgeBase.getKbCode());
        }
    }

    /** 对批大小做兜底，避免缺配置或非法配置导致整条链路不可用。 */
    private int normalizeBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize < 1) {
            return 16;
        }
        return batchSize;
    }

    /** 把 embedding 结果转换成 pgvector 可写入的文本字面量。 */
    private String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new BusinessException("Embedding vector must not be empty");
        }
        return "[" + vector.stream()
                .map(value -> String.format(Locale.ROOT, "%.12f", value))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]";
    }

    /** 截断错误信息，避免数据库字段被异常长消息撑爆。 */
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
