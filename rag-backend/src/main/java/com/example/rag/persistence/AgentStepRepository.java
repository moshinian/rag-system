package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.AgentStepMapper;
import com.example.rag.persistence.entity.AgentStepEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行步骤访问层。
 */
@Repository
public class AgentStepRepository {
    private final AgentStepMapper mapper;

    /** 构造AgentStepRepository。 */
    public AgentStepRepository(AgentStepMapper mapper) {
        this.mapper = mapper;
    }

    /** 新增执行步骤。 */
    public AgentStepEntity insert(AgentStepEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    /** 更新执行步骤。 */
    public AgentStepEntity updateById(AgentStepEntity entity) {
        mapper.updateById(entity);
        return entity;
    }

    /** 按主键查询执行步骤。 */
    public Optional<AgentStepEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 按运行编码查询执行步骤。 */
    public List<AgentStepEntity> findByRunCode(String runCode) {
        LambdaQueryWrapper<AgentStepEntity> query = new LambdaQueryWrapper<AgentStepEntity>()
                .eq(AgentStepEntity::getRunCode, runCode)
                .orderByAsc(AgentStepEntity::getCreatedAt);
        return mapper.selectList(query);
    }
}
