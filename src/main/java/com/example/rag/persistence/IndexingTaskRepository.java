package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.model.enums.IndexingTaskStatus;
import com.example.rag.mapper.IndexingTaskMapper;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /** 按主键查询任务。 */
    public Optional<IndexingTaskEntity> findById(Long id) {
        return Optional.ofNullable(indexingTaskMapper.selectById(id));
    }

    /** 按文档倒序读取处理任务。 */
    public List<IndexingTaskEntity> findByDocumentIdOrderByCreatedAtDesc(Long documentId) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getDocumentId, documentId)
                .orderByDesc(IndexingTaskEntity::getCreatedAt)
                .orderByDesc(IndexingTaskEntity::getId);
        return indexingTaskMapper.selectList(query);
    }

    /** 判断文档下是否存在未结束的同类型任务。 */
    public boolean existsActiveTask(Long documentId, String taskType) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getDocumentId, documentId)
                .eq(IndexingTaskEntity::getTaskType, taskType)
                .in(IndexingTaskEntity::getStatus, List.of(IndexingTaskStatus.QUEUED, IndexingTaskStatus.RUNNING));
        return indexingTaskMapper.selectCount(query) > 0;
    }
}
