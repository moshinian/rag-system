package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.EmbeddingRebuildRunMapper;
import com.example.rag.model.enums.EmbeddingRebuildRunStatus;
import com.example.rag.persistence.entity.EmbeddingRebuildRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 全量重嵌入运行记录访问层。
 */
@Repository
public class EmbeddingRebuildRunRepository {
    private final EmbeddingRebuildRunMapper mapper;

    /** 构造EmbeddingRebuildRunRepository。 */
    public EmbeddingRebuildRunRepository(EmbeddingRebuildRunMapper mapper) {
        this.mapper = mapper;
    }

    /** 新增数据记录。 */
    public EmbeddingRebuildRunEntity insert(EmbeddingRebuildRunEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    /** 更新数据记录。 */
    public EmbeddingRebuildRunEntity updateById(EmbeddingRebuildRunEntity entity) {
        mapper.updateById(entity);
        return entity;
    }

    /** 按条件查询数据。 */
    public Optional<EmbeddingRebuildRunEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 判断数据是否存在。 */
    public boolean existsActiveRun() {
        LambdaQueryWrapper<EmbeddingRebuildRunEntity> query = new LambdaQueryWrapper<EmbeddingRebuildRunEntity>()
                .in(EmbeddingRebuildRunEntity::getStatus, List.of(
                        EmbeddingRebuildRunStatus.QUEUED,
                        EmbeddingRebuildRunStatus.RUNNING,
                        EmbeddingRebuildRunStatus.CANCELLING
                ));
        return mapper.selectCount(query) > 0;
    }

    /** 按条件查询数据。 */
    public List<EmbeddingRebuildRunEntity> findByStatuses(List<EmbeddingRebuildRunStatus> statuses, int limit) {
        LambdaQueryWrapper<EmbeddingRebuildRunEntity> query = new LambdaQueryWrapper<EmbeddingRebuildRunEntity>()
                .in(EmbeddingRebuildRunEntity::getStatus, statuses)
                .orderByAsc(EmbeddingRebuildRunEntity::getCreatedAt)
                .last("limit " + Math.max(1, limit));
        return mapper.selectList(query);
    }
}
