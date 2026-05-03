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

/**
 * 文档切块持久化访问层。
 */
@Repository
public class DocumentChunkRepository {

    private final DocumentChunkMapper documentChunkMapper;

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

    /** 批量新增 chunk。 */
    public List<DocumentChunkEntity> batchInsert(List<DocumentChunkEntity> entities) {
        for (DocumentChunkEntity entity : entities) {
            documentChunkMapper.insert(entity);
        }
        return entities;
    }

    /** 统计某个知识库下的 chunk 总量。 */
    public long countByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getKnowledgeBaseId, knowledgeBaseId);
        return documentChunkMapper.selectCount(query);
    }

    /** 统计某个知识库下处于指定向量状态的 chunk 数量。 */
    public long countByKnowledgeBaseIdAndEmbeddingStatus(Long knowledgeBaseId, EmbeddingStatus embeddingStatus) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(DocumentChunkEntity::getEmbeddingStatus, embeddingStatus);
        return documentChunkMapper.selectCount(query);
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
                                     String embeddingErrorMessage,
                                     OffsetDateTime embeddingUpdatedAt) {
        documentChunkMapper.updateEmbeddingState(
                chunkId,
                embeddingStatus.name(),
                embeddingModel,
                embeddingErrorMessage,
                embeddingUpdatedAt
        );
    }

    /** 写入 chunk 向量并更新向量状态。 */
    public void updateEmbeddingVector(Long chunkId,
                                      EmbeddingStatus embeddingStatus,
                                      String embeddingModel,
                                      String embeddingVectorLiteral,
                                      OffsetDateTime embeddingUpdatedAt) {
        documentChunkMapper.updateEmbeddingVector(
                chunkId,
                embeddingStatus.name(),
                embeddingModel,
                embeddingVectorLiteral,
                embeddingUpdatedAt
        );
    }

    /** 按知识库执行 TopK 向量召回。 */
    public List<RetrievedChunkCandidate> findTopKSimilarChunks(Long knowledgeBaseId,
                                                               String queryVectorLiteral,
                                                               int topK) {
        return documentChunkMapper.findTopKSimilarChunks(knowledgeBaseId, queryVectorLiteral, topK);
    }
}
