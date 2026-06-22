package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.model.enums.IndexingTaskStatus;
import com.example.rag.mapper.IndexingTaskMapper;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档处理任务持久化访问层。
 */
@Repository
public class IndexingTaskRepository {
    private final IndexingTaskMapper indexingTaskMapper;

    /** 构造IndexingTaskRepository。 */
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

    /** 按文档和任务类型倒序读取任务。 */
    public List<IndexingTaskEntity> findByDocumentIdAndTaskTypeOrderByCreatedAtDesc(Long documentId, String taskType) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getDocumentId, documentId)
                .eq(IndexingTaskEntity::getTaskType, taskType)
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

    /** 判断文档下是否存在除指定任务外的未结束任务。 */
    public boolean existsOtherActiveTask(Long documentId, String taskType, Long excludedTaskId) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getDocumentId, documentId)
                .eq(IndexingTaskEntity::getTaskType, taskType)
                .in(IndexingTaskEntity::getStatus, List.of(IndexingTaskStatus.QUEUED, IndexingTaskStatus.RUNNING))
                .ne(IndexingTaskEntity::getId, excludedTaskId);
        return indexingTaskMapper.selectCount(query) > 0;
    }

    /** 判断知识库下是否存在未结束的同类型任务。 */
    public boolean existsActiveTaskInKnowledgeBase(Long knowledgeBaseId, String taskType) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(IndexingTaskEntity::getTaskType, taskType)
                .in(IndexingTaskEntity::getStatus, List.of(IndexingTaskStatus.QUEUED, IndexingTaskStatus.RUNNING));
        return indexingTaskMapper.selectCount(query) > 0;
    }

    /** 判断系统内是否存在未结束的同类型任务。 */
    public boolean existsAnyActiveTask(String taskType) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getTaskType, taskType)
                .in(IndexingTaskEntity::getStatus, List.of(IndexingTaskStatus.QUEUED, IndexingTaskStatus.RUNNING));
        return indexingTaskMapper.selectCount(query) > 0;
    }

    /** 读取可恢复的卡住任务。 */
    public List<IndexingTaskEntity> findRecoverableTasks(String taskType, OffsetDateTime cutoff, int limit) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getTaskType, taskType)
                .in(IndexingTaskEntity::getStatus, List.of(IndexingTaskStatus.QUEUED, IndexingTaskStatus.RUNNING))
                .and(wrapper -> wrapper
                        .lt(IndexingTaskEntity::getLastHeartbeatAt, cutoff)
                        .or(inner -> inner.isNull(IndexingTaskEntity::getLastHeartbeatAt)
                                .lt(IndexingTaskEntity::getStartedAt, cutoff)))
                .orderByAsc(IndexingTaskEntity::getStartedAt)
                .orderByAsc(IndexingTaskEntity::getId)
                .last("LIMIT " + limit);
        return indexingTaskMapper.selectList(query);
    }

    /** 按知识库、状态和创建时间倒序扫描任务。 */
    public List<IndexingTaskEntity> findByKnowledgeBaseIdAndStatusesOrderByCreatedAtDesc(Long knowledgeBaseId,
                                                                                          List<IndexingTaskStatus> statuses,
                                                                                          int limit) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(IndexingTaskEntity::getCreatedAt)
                .orderByDesc(IndexingTaskEntity::getId)
                .last("LIMIT " + Math.max(1, limit));
        if (statuses != null && !statuses.isEmpty()) {
            query.in(IndexingTaskEntity::getStatus, statuses);
        }
        return indexingTaskMapper.selectList(query);
    }

    /** 按知识库删除全部任务。 */
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<IndexingTaskEntity> query = new LambdaQueryWrapper<IndexingTaskEntity>()
                .eq(IndexingTaskEntity::getKnowledgeBaseId, knowledgeBaseId);
        indexingTaskMapper.delete(query);
    }
}
