package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.AgentActionMapper;
import com.example.rag.persistence.entity.AgentActionEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 推荐动作访问层。
 */
@Repository
public class AgentActionRepository {
    private final AgentActionMapper mapper;

    /** 构造AgentActionRepository。 */
    public AgentActionRepository(AgentActionMapper mapper) {
        this.mapper = mapper;
    }

    /** 新增推荐动作。 */
    public AgentActionEntity insert(AgentActionEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    /** 更新推荐动作。 */
    public AgentActionEntity updateById(AgentActionEntity entity) {
        mapper.updateById(entity);
        return entity;
    }

    /** 按主键查询推荐动作。 */
    public Optional<AgentActionEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按业务编码查询推荐动作。 */
    public Optional<AgentActionEntity> findByActionCode(String actionCode) {
        LambdaQueryWrapper<AgentActionEntity> query = new LambdaQueryWrapper<AgentActionEntity>()
                .eq(AgentActionEntity::getActionCode, actionCode);
        return Optional.ofNullable(mapper.selectOne(query));
    }

    /** 按运行编码查询推荐动作。 */
    public List<AgentActionEntity> findByRunCode(String runCode) {
        LambdaQueryWrapper<AgentActionEntity> query = new LambdaQueryWrapper<AgentActionEntity>()
                .eq(AgentActionEntity::getRunCode, runCode)
                .orderByAsc(AgentActionEntity::getCreatedAt);
        return mapper.selectList(query);
    }
}
