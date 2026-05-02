package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.DocumentChunkMapper;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import org.springframework.stereotype.Repository;

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
}
