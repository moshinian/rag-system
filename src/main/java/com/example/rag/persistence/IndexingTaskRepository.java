package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.IndexingTaskMapper;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档处理任务持久化访问层。
 */
@Repository
public class IndexingTaskRepository {

    private final IndexingTaskMapper indexingTaskMapper;

    public IndexingTaskRepository(IndexingTaskMapper indexingTaskMapper) {
        this.indexingTaskMapper = indexingTaskMapper;
    }

    /** 新增任务记录。 */
    public IndexingTaskEntity insert(IndexingTaskEntity entity) {
        indexingTaskMapper.insert(entity);
        return entity;
    }

    /** 按主键更新任务记录。 */
    public IndexingTaskEntity updateById(IndexingTaskEntity entity) {
        indexingTaskMapper.updateById(entity);
        return entity;
    }

    /** 按文档倒序读取处理任务。 */
    public List<IndexingTaskEntity> findByDocumentIdOrderByCreatedAtDesc(Long documentId) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getDocumentId, documentId)
                .orderByDesc(IndexingTaskEntity::getCreatedAt)
                .orderByDesc(IndexingTaskEntity::getId);
        return indexingTaskMapper.selectList(query);
    }
}
