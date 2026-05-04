package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.CacheNames;
import com.example.rag.integration.llm.OpenAiCompatibleClient;
import com.example.rag.model.dto.RetrievedChunkCandidate;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.cache.annotation.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 问答检索服务。
 *
 * 负责提供问答链路就绪度检查，以及基于向量的 TopK 检索能力。
 */
@Service
public class QuestionAnsweringService {

    private static final Logger log = LoggerFactory.getLogger(QuestionAnsweringService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final RagRetrievalProperties ragRetrievalProperties;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    public QuestionAnsweringService(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    RagEmbeddingProperties ragEmbeddingProperties,
                                    RagRetrievalProperties ragRetrievalProperties,
                                    OpenAiCompatibleClient openAiCompatibleClient) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.ragRetrievalProperties = ragRetrievalProperties;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    /** 返回指定知识库当前是否具备进入检索和问答阶段的前置条件。 */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.QA_READINESS, key = "#kbCode")
    public QuestionAnsweringReadinessResponse getReadiness(String kbCode) {
        KnowledgeBaseEntity knowledgeBase = getKnowledgeBase(kbCode);

        long indexedChunkCount = documentChunkRepository.countByKnowledgeBaseId(knowledgeBase.getId());
        long embeddedChunkCount = documentChunkRepository.countByKnowledgeBaseIdAndEmbeddingStatus(
                knowledgeBase.getId(),
                EmbeddingStatus.EMBEDDED
        );

        return new QuestionAnsweringReadinessResponse(
                knowledgeBase.getKbCode(),
                knowledgeBase.getStatus().name(),
                indexedChunkCount > 0 && embeddedChunkCount > 0,
                ragEmbeddingProperties.getProvider(),
                ragEmbeddingProperties.getModel(),
                ragEmbeddingProperties.getVectorDimensions(),
                ragRetrievalProperties.getVectorStore(),
                ragRetrievalProperties.getDefaultTopK(),
                indexedChunkCount,
                embeddedChunkCount,
                resolveNextStep(indexedChunkCount, embeddedChunkCount)
        );
    }

    /** 给调用方返回下一步建议，便于排查当前卡在哪个前置条件上。 */
    private String resolveNextStep(long indexedChunkCount, long embeddedChunkCount) {
        if (indexedChunkCount <= 0) {
            return "Process at least one document into chunks before running retrieval.";
        }
        if (embeddedChunkCount <= 0) {
            return "Generate embeddings for existing chunks before running retrieval.";
        }
        return "Retrieval prerequisites are ready.";
    }

    /** 对指定知识库执行向量检索，并返回命中的 chunk 列表。 */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.QA_RETRIEVAL,
            key = "#kbCode + ':' + #question + ':' + (#topK == null ? 'null' : #topK)"
    )
    public QuestionRetrievalResponse retrieve(String kbCode, String question, Integer topK) {
        KnowledgeBaseEntity knowledgeBase = getKnowledgeBase(kbCode);
        String normalizedQuestion = normalizeQuestion(question);
        int resolvedTopK = resolveTopK(topK);
        log.info(StructuredLogMessage.of("qa.retrieve.started")
                .field("kbCode", kbCode)
                .field("topK", resolvedTopK)
                .field("questionLength", normalizedQuestion.length())
                .build());

        List<Double> queryVector = openAiCompatibleClient.createEmbedding(
                ragEmbeddingProperties.getBaseUrl(),
                ragEmbeddingProperties.getApiKey(),
                ragEmbeddingProperties.getEmbeddingPath(),
                ragEmbeddingProperties.getModel(),
                normalizedQuestion
        );
        // pgvector 查询当前使用文本字面量格式，因此这里先把向量转换成 SQL 可识别的字符串。
        String queryVectorLiteral = toVectorLiteral(queryVector);

        List<RetrievedChunkResponse> chunks = documentChunkRepository.findTopKSimilarChunks(
                        knowledgeBase.getId(),
                        queryVectorLiteral,
                        resolvedTopK
                ).stream()
                .map(this::toRetrievedChunkResponse)
                .toList();

        log.info(StructuredLogMessage.of("qa.retrieve.completed")
                .field("kbCode", kbCode)
                .field("topK", resolvedTopK)
                .field("retrievedChunkCount", chunks.size())
                .field("embeddingModel", ragEmbeddingProperties.getModel())
                .build());

        return new QuestionRetrievalResponse(
                knowledgeBase.getKbCode(),
                normalizedQuestion,
                ragEmbeddingProperties.getModel(),
                resolvedTopK,
                chunks.size(),
                chunks
        );
    }

    /** 读取知识库，不存在时统一抛业务异常。 */
    private KnowledgeBaseEntity getKnowledgeBase(String kbCode) {
        return knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
    }

    /** 统一校验并清理问题文本。 */
    private String normalizeQuestion(String question) {
        if (question == null || question.trim().isBlank()) {
            throw new BusinessException("Question must not be blank");
        }
        return question.trim();
    }

    /** 结合默认值和最大值限制，解析最终的检索条数。 */
    private int resolveTopK(Integer topK) {
        int fallbackTopK = ragRetrievalProperties.getDefaultTopK() == null ? 5 : ragRetrievalProperties.getDefaultTopK();
        int maxTopK = ragRetrievalProperties.getMaxTopK() == null ? 10 : ragRetrievalProperties.getMaxTopK();
        int resolvedTopK = topK == null ? fallbackTopK : topK;
        if (resolvedTopK < 1) {
            throw new BusinessException("topK must be >= 1");
        }
        if (resolvedTopK > maxTopK) {
            throw new BusinessException("topK must be <= " + maxTopK);
        }
        return resolvedTopK;
    }

    /** 把向量转换成 pgvector 可消费的字面量格式。 */
    private String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new BusinessException("Query embedding vector must not be empty");
        }
        return "[" + vector.stream()
                .map(value -> String.format(Locale.ROOT, "%.12f", value))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]";
    }

    /** 把持久层候选结果转换成接口返回对象。 */
    private RetrievedChunkResponse toRetrievedChunkResponse(RetrievedChunkCandidate chunk) {
        return new RetrievedChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getDocumentCode(),
                chunk.getDocumentName(),
                chunk.getChunkIndex(),
                chunk.getChunkType(),
                chunk.getContent(),
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                chunk.getEmbeddingModel(),
                chunk.getScore()
        );
    }
}
