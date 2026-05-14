package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.DocumentChunkMapper;
import com.example.rag.model.dto.RetrievedChunkCandidate;
import com.example.rag.model.enums.DocumentChunkStatus;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档切块持久化访问层。
 */
@Repository
public class DocumentChunkRepository {
    private final DocumentChunkMapper documentChunkMapper;

    /** 构造DocumentChunkRepository。 */
    public DocumentChunkRepository(DocumentChunkMapper documentChunkMapper) {
        this.documentChunkMapper = documentChunkMapper;
    }

    /** 按顺序读取某篇文档的全部 chunk。 */
    public List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(Long documentId) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getDocumentId, documentId)
                .orderByAsc(DocumentChunkEntity::getChunkIndex);
        return documentChunkMapper.selectList(query);
    }

    /** 删除某篇文档的旧 chunk，便于重新处理。 */
    public void deleteByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getDocumentId, documentId);
        documentChunkMapper.delete(query);
    }

    /** 按知识库删除全部 chunk。 */
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getKnowledgeBaseId, knowledgeBaseId);
        documentChunkMapper.delete(query);
    }

    /** 批量新增 chunk。 */
    public List<DocumentChunkEntity> batchInsert(List<DocumentChunkEntity> entities) {
        for (DocumentChunkEntity entity : entities) {
            documentChunkMapper.insert(entity);
        }
        return entities;
    }

    /** 统计某个知识库下当前可参与检索的已切块数量。 */
    public long countAvailableIndexedChunks(Long knowledgeBaseId) {
        return documentChunkMapper.countAvailableIndexedChunks(knowledgeBaseId);
    }

    /** 统计某个知识库下当前可参与检索的已向量化数量。 */
    public long countAvailableEmbeddedChunks(Long knowledgeBaseId) {
        return documentChunkMapper.countAvailableEmbeddedChunks(knowledgeBaseId);
    }

    /** 统计某个知识库下维度与当前配置不一致的已向量化 chunk 数量。 */
    public long countEmbeddedChunksWithDifferentDimensions(Long knowledgeBaseId, int expectedDimensions) {
        return documentChunkMapper.countEmbeddedChunksWithDifferentDimensions(knowledgeBaseId, expectedDimensions);
    }

    /** 检查活动知识库中是否仍存在需要人工重嵌入的旧向量。 */
    public boolean existsEmbeddedChunksNeedingRebuild(String currentFingerprint, int expectedDimensions) {
        return documentChunkMapper.existsEmbeddedChunksNeedingRebuild(currentFingerprint, expectedDimensions);
    }

    /** 返回活动知识库中最近一次写入的 embedding 模型名称。 */
    public Optional<String> findLatestEmbeddedModelInActiveKnowledgeBases() {
        return Optional.ofNullable(documentChunkMapper.findLatestEmbeddedModelInActiveKnowledgeBases());
    }

    /** 按文档和 embedding 状态读取可继续向量化的 chunk。 */
    public List<DocumentChunkEntity> findEmbeddableChunksByDocumentId(Long documentId,
                                                                      List<EmbeddingStatus> embeddingStatuses,
                                                                      int limit) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getDocumentId, documentId)
                .eq(DocumentChunkEntity::getStatus, DocumentChunkStatus.ACTIVE)
                .in(DocumentChunkEntity::getEmbeddingStatus, embeddingStatuses)
                .orderByAsc(DocumentChunkEntity::getChunkIndex)
                .last("LIMIT " + limit);
        return documentChunkMapper.selectList(query);
    }

    /** 统计某篇文档下指定向量状态的 chunk 数量。 */
    public long countByDocumentIdAndEmbeddingStatus(Long documentId, EmbeddingStatus embeddingStatus) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getDocumentId, documentId)
                .eq(DocumentChunkEntity::getEmbeddingStatus, embeddingStatus);
        return documentChunkMapper.selectCount(query);
    }

    /** 更新 chunk 的向量化状态。 */
    public void updateEmbeddingState(Long chunkId,
                                     EmbeddingStatus embeddingStatus,
                                     String embeddingModel,
                                     String embeddingProvider,
                                     String embeddingProfileFingerprint,
                                     Long embeddingRebuildRunId,
                                     String embeddingUpdatedBy,
                                     String embeddingErrorMessage,
                                     OffsetDateTime embeddingUpdatedAt) {
        documentChunkMapper.updateEmbeddingState(
                chunkId,
                embeddingStatus.name(),
                embeddingModel,
                embeddingProvider,
                embeddingProfileFingerprint,
                embeddingRebuildRunId,
                embeddingUpdatedBy,
                embeddingErrorMessage,
                embeddingUpdatedAt
        );
    }

    /** 写入 chunk 向量并更新向量状态。 */
    public void updateEmbeddingVector(Long chunkId,
                                      EmbeddingStatus embeddingStatus,
                                      String embeddingModel,
                                      String embeddingProvider,
                                      String embeddingProfileFingerprint,
                                      Long embeddingRebuildRunId,
                                      String embeddingUpdatedBy,
                                      String embeddingVectorLiteral,
                                      OffsetDateTime embeddingUpdatedAt) {
        documentChunkMapper.updateEmbeddingVector(
                chunkId,
                embeddingStatus.name(),
                embeddingModel,
                embeddingProvider,
                embeddingProfileFingerprint,
                embeddingRebuildRunId,
                embeddingUpdatedBy,
                embeddingVectorLiteral,
                embeddingUpdatedAt
        );
    }

    /** 为全量重嵌入统一清空旧向量并标记待处理。 */
    public void resetEmbeddingsForRebuild(String embeddingModel,
                                          String embeddingProvider,
                                          String embeddingProfileFingerprint,
                                          Long embeddingRebuildRunId,
                                          String embeddingUpdatedBy,
                                          OffsetDateTime embeddingUpdatedAt) {
        documentChunkMapper.resetEmbeddingsForRebuild(
                embeddingModel,
                embeddingProvider,
                embeddingProfileFingerprint,
                embeddingRebuildRunId,
                embeddingUpdatedBy,
                embeddingUpdatedAt
        );
    }

    /** 按知识库执行 TopK 向量召回。 */
    public List<RetrievedChunkCandidate> findTopKSimilarChunks(Long knowledgeBaseId,
                                                               String queryVectorLiteral,
                                                               int topK) {
        return documentChunkMapper.findTopKSimilarChunks(knowledgeBaseId, queryVectorLiteral, topK);
    }

    /** 按知识库执行第一版关键词召回。 */
    public List<RetrievedChunkCandidate> findTopKeywordChunks(Long knowledgeBaseId,
                                                              String questionPhrase,
                                                              List<String> terms,
                                                              double phraseWeight,
                                                              double titleWeight,
                                                              double minHitThreshold,
                                                              int limit) {
        return documentChunkMapper.findTopKeywordChunksByLike(
                knowledgeBaseId,
                questionPhrase,
                terms,
                phraseWeight,
                titleWeight,
                minHitThreshold,
                limit
        );
    }

    /** 按知识库执行 PostgreSQL FTS 词法召回。 */
    public List<RetrievedChunkCandidate> findTopKeywordChunksByFts(Long knowledgeBaseId,
                                                                   String queryText,
                                                                   String tsConfig,
                                                                   String rankFunction,
                                                                   int limit) {
        return documentChunkMapper.findTopKeywordChunksByFts(
                knowledgeBaseId,
                queryText,
                tsConfig,
                rankFunction,
                limit
        );
    }
}
