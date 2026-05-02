package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.mapper.KnowledgeBaseMapper;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.KnowledgeBasePageQuery;
import com.example.rag.persistence.query.PageResult;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 知识库持久化访问层。
 */
@Repository
public class KnowledgeBaseRepository {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KnowledgeBaseRepository(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    /** 按知识库主键查询。 */
    public Optional<KnowledgeBaseEntity> findById(Long id) {
        return Optional.ofNullable(knowledgeBaseMapper.selectById(id));
    }

    /** 按知识库编码查询。 */
    public Optional<KnowledgeBaseEntity> findByCode(String kbCode) {
        LambdaQueryWrapper<KnowledgeBaseEntity> query = new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getKbCode, kbCode)
                .last("LIMIT 1");
        return Optional.ofNullable(knowledgeBaseMapper.selectOne(query));
    }

    /** 分页查询知识库。 */
    public PageResult<KnowledgeBaseEntity> page(KnowledgeBasePageQuery pageQuery) {
        LambdaQueryWrapper<KnowledgeBaseEntity> query = new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .orderByDesc(KnowledgeBaseEntity::getCreatedAt)
                .orderByDesc(KnowledgeBaseEntity::getId);
        if (pageQuery.status() != null) {
            query.eq(KnowledgeBaseEntity::getStatus, pageQuery.status());
        }

        Page<KnowledgeBaseEntity> page = new Page<>(pageQuery.pageNo(), pageQuery.pageSize());
        Page<KnowledgeBaseEntity> result = knowledgeBaseMapper.selectPage(page, query);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 新增知识库。 */
    public KnowledgeBaseEntity insert(KnowledgeBaseEntity entity) {
        knowledgeBaseMapper.insert(entity);
        return entity;
    }

    /** 按主键更新知识库。 */
    public KnowledgeBaseEntity updateById(KnowledgeBaseEntity entity) {
        knowledgeBaseMapper.updateById(entity);
        return entity;
    }
}
