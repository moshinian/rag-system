package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.CacheNames;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.integration.ai.AiGatewayClient;
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
    private static final int DASHSCOPE_MAX_BATCH_SIZE = 10;
    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final AiGatewayClient aiGatewayClient;
    private final EmbeddingConfigurationStateService embeddingConfigurationStateService;

    /** 注入文档向量化所需依赖。 */
    public DocumentEmbeddingService(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentRepository documentRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    IndexingTaskRepository indexingTaskRepository,
                                    RagEmbeddingProperties ragEmbeddingProperties,
                                    AiGatewayClient aiGatewayClient,
                                    EmbeddingConfigurationStateService embeddingConfigurationStateService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.indexingTaskRepository = indexingTaskRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.aiGatewayClient = aiGatewayClient;
        this.embeddingConfigurationStateService = embeddingConfigurationStateService;
    }

    /** 对指定文档的 chunk 执行向量化并写入 pgvector。 */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DOCUMENT_CHUNKS, key = "#kbCode + ':' + #documentCode"),
            @CacheEvict(cacheNames = CacheNames.QA_READINESS, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true)
    })
    public DocumentEmbeddingResponse embed(String kbCode, String documentCode) {
        return embedInternal(kbCode, documentCode, false, buildContext("system", null));
    }

    /** 异步索引链路内部调用时允许复用 embed 逻辑，但要绕过“活动索引任务”自校验。 */
    DocumentEmbeddingResponse embedForIndexing(String kbCode, String documentCode) {
        return embedInternal(kbCode, documentCode, true, buildContext("system", null));
    }

    /** 全量重嵌入内部入口，记录操作人和 rebuild run 关联信息。 */
    DocumentEmbeddingResponse embedForRebuild(String kbCode, String documentCode, String operator, Long rebuildRunId) {
        return embedInternal(kbCode, documentCode, true, buildContext(operator, rebuildRunId));
    }

    /** 执行向量化主流程，并按场景决定是否跳过活动任务校验。 */
    private DocumentEmbeddingResponse embedInternal(String kbCode,
                                                    String documentCode,
                                                    boolean allowDuringActiveIndexing,
                                                    EmbeddingWriteContext context) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);

        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        // 这里只接受已经完成切块的文档，避免把“无 chunk 可写向量”误当成成功。
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
                        context.embeddingProvider(),
                        context.embeddingProfileFingerprint(),
                        context.embeddingRebuildRunId(),
                        context.embeddingUpdatedBy(),
                        null,
                        startedAt
                );
            }

            try {
                List<List<Double>> embeddings = aiGatewayClient.createEmbeddings(
                        ragEmbeddingProperties.getModel(),
                        chunks.stream().map(chunk -> chunk.getContent()).toList()
                );
                if (embeddings.size() != chunks.size()) {
                    // 远端返回条数和输入条数不一致时，宁可整批失败，也不冒险错位写向量。
                    throw new BusinessException("Embedding result size does not match chunk count");
                }
                validateEmbeddingDimensions(embeddings);

                OffsetDateTime updatedAt = OffsetDateTime.now();
                // 接口返回顺序和输入顺序一一对应，逐条回写到原始 chunk 即可。
                for (int index = 0; index < chunks.size(); index++) {
                    DocumentChunkEntity chunk = chunks.get(index);
                    String vectorLiteral = toVectorLiteral(embeddings.get(index));
                    documentChunkRepository.updateEmbeddingVector(
                            chunk.getId(),
                            EmbeddingStatus.EMBEDDED,
                            ragEmbeddingProperties.getModel(),
                            context.embeddingProvider(),
                            context.embeddingProfileFingerprint(),
                            context.embeddingRebuildRunId(),
                            context.embeddingUpdatedBy(),
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
                // 批次内任何一条失败都统一回滚为 FAILED，等待下一次补偿或手工重试。
                for (DocumentChunkEntity chunk : chunks) {
                    documentChunkRepository.updateEmbeddingState(
                            chunk.getId(),
                            EmbeddingStatus.FAILED,
                            ragEmbeddingProperties.getModel(),
                            context.embeddingProvider(),
                            context.embeddingProfileFingerprint(),
                            context.embeddingRebuildRunId(),
                            context.embeddingUpdatedBy(),
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
        int normalized = (batchSize == null || batchSize < 1) ? 10 : batchSize;
        if (usesDashScopeCompatibleEmbeddings()) {
            // DashScope 兼容接口对批大小更敏感，这里统一收敛到已验证上限。
            return Math.min(normalized, DASHSCOPE_MAX_BATCH_SIZE);
        }
        return normalized;
    }

    /** 校验 embedding 维度是否一致。 */
    private void validateEmbeddingDimensions(List<List<Double>> embeddings) {
        int expectedDimensions = ragEmbeddingProperties.getVectorDimensions() == null ? 0 : ragEmbeddingProperties.getVectorDimensions();
        if (expectedDimensions <= 0) {
            throw new BusinessException("Embedding vectorDimensions must be > 0");
        }
        for (List<Double> embedding : embeddings) {
            if (embedding == null || embedding.size() != expectedDimensions) {
                throw new BusinessException("Embedding vector dimensions do not match configured vectorDimensions: expected "
                        + expectedDimensions + ", actual " + (embedding == null ? 0 : embedding.size()));
            }
        }
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

    /** 构造 embedding 写入上下文。 */
    private EmbeddingWriteContext buildContext(String operator, Long rebuildRunId) {
        // fingerprint 和 provider 一起写回 chunk，后续 readiness 与 rebuild 判断都依赖这组元数据。
        return new EmbeddingWriteContext(
                ragEmbeddingProperties.getProvider(),
                embeddingConfigurationStateService.getRequiredState().getCurrentConfigFingerprint(),
                rebuildRunId,
                operator == null || operator.isBlank() ? "system" : operator.trim()
        );
    }

    /** 判断当前是否使用 DashScope 兼容 embedding 接口。 */
    private boolean usesDashScopeCompatibleEmbeddings() {
        String provider = ragEmbeddingProperties.getProvider();
        return provider != null && provider.toLowerCase(Locale.ROOT).contains("aliyun");
    }

    /** 封装一次 embedding 写入所需的上下文字段。 */
    private record EmbeddingWriteContext(String embeddingProvider,
                                         String embeddingProfileFingerprint,
                                         Long embeddingRebuildRunId,
                                         String embeddingUpdatedBy) {
    }
}
