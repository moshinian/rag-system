package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.AgentRunMapper;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Agent 运行记录访问层。
 */
@Repository
public class AgentRunRepository {
    private final AgentRunMapper mapper;

    /** 构造AgentRunRepository。 */
    public AgentRunRepository(AgentRunMapper mapper) {
        this.mapper = mapper;
    }

    /** 新增运行记录。 */
    public AgentRunEntity insert(AgentRunEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    /** 更新运行记录。 */
    public AgentRunEntity updateById(AgentRunEntity entity) {
        mapper.updateById(entity);
        return entity;
    }

    /** 按主键查询运行记录。 */
    public Optional<AgentRunEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按业务编码查询运行记录。 */
    public Optional<AgentRunEntity> findByRunCode(String runCode) {
        LambdaQueryWrapper<AgentRunEntity> query = new LambdaQueryWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getRunCode, runCode);
        return Optional.ofNullable(mapper.selectOne(query));
    }
}
