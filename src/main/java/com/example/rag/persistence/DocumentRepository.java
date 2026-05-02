package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.mapper.DocumentMapper;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.query.DocumentPageQuery;
import com.example.rag.persistence.query.PageResult;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档主表持久化访问层。
 */
@Repository
public class DocumentRepository {

    private final DocumentMapper documentMapper;

    public DocumentRepository(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /** 按文档编码查询。 */
    public Optional<DocumentEntity> findByCode(String documentCode) {
        LambdaQueryWrapper<DocumentEntity> query = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getDocumentCode, documentCode)
                .last("LIMIT 1");
        return Optional.ofNullable(documentMapper.selectOne(query));
    }

    /** 同时按知识库编码和文档编码查询，避免跨知识库误命中。 */
    public Optional<DocumentEntity> findByCodeInKnowledgeBase(String documentCode, String kbCode) {
        return Optional.ofNullable(documentMapper.selectByCodeInKnowledgeBase(documentCode, kbCode));
    }

    /** 基于知识库和内容摘要做基础防重。 */
    public boolean existsInKnowledgeBaseByContentHash(Long knowledgeBaseId, String contentHash) {
        LambdaQueryWrapper<DocumentEntity> query = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(DocumentEntity::getContentHash, contentHash);
        return documentMapper.selectCount(query) > 0;
    }

    /** 按知识库分页查询文档。 */
    public PageResult<DocumentEntity> pageByKnowledgeBase(DocumentPageQuery pageQuery) {
        LambdaQueryWrapper<DocumentEntity> query = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getKnowledgeBaseId, pageQuery.knowledgeBaseId())
                .orderByDesc(DocumentEntity::getCreatedAt)
                .orderByDesc(DocumentEntity::getId);
        if (pageQuery.status() != null) {
            query.eq(DocumentEntity::getStatus, pageQuery.status());
        }

        Page<DocumentEntity> page = new Page<>(pageQuery.pageNo(), pageQuery.pageSize());
        Page<DocumentEntity> result = documentMapper.selectPage(page, query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 新增文档。 */
    public DocumentEntity insert(DocumentEntity entity) {
        documentMapper.insert(entity);
        return entity;
    }

    /** 按主键更新文档。 */
    public DocumentEntity updateById(DocumentEntity entity) {
        documentMapper.updateById(entity);
        return entity;
    }
}
