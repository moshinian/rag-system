package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问答链路服务。
 *
 * Day 8 先提供“链路是否就绪”的可观测入口，
 * 为 Day 9/10 的向量化和检索实现提供清晰起点。
 */
@Service
public class QuestionAnsweringService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final RagRetrievalProperties ragRetrievalProperties;

    public QuestionAnsweringService(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    RagEmbeddingProperties ragEmbeddingProperties,
                                    RagRetrievalProperties ragRetrievalProperties) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.ragRetrievalProperties = ragRetrievalProperties;
    }

    /** 返回指定知识库的 Week 2 问答链路就绪度。 */
    @Transactional(readOnly = true)
    public QuestionAnsweringReadinessResponse getReadiness(String kbCode) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

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

    private String resolveNextStep(long indexedChunkCount, long embeddedChunkCount) {
        if (indexedChunkCount <= 0) {
            return "Process at least one document into chunks before starting the Week 2 pipeline.";
        }
        if (embeddedChunkCount <= 0) {
            return "Run the Day 9 embedding pipeline to generate vectors for the existing chunks.";
        }
        return "Embedding prerequisites are ready. Proceed to Day 10 retrieval implementation.";
    }
}
