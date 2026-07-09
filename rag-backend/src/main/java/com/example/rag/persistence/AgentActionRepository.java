package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.AgentActionMapper;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.persistence.entity.AgentActionEntity;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Agent 推荐动作访问层。
 */
@Repository
@SuppressWarnings("null") // MyBatis-Plus lambda 字段引用会被 JDT 误判为 NonNull 泛型转换告警。
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

    /** 判断 run 是否存在待人工确认动作。 */
    public boolean existsPendingConfirmation(String runCode) {
        LambdaQueryWrapper<AgentActionEntity> query = new LambdaQueryWrapper<AgentActionEntity>()
                .eq(AgentActionEntity::getRunCode, runCode)
                .eq(AgentActionEntity::getStatus, AgentActionStatus.PENDING_CONFIRMATION);
        return mapper.selectCount(query) > 0;
    }

    /** 按运行编码批量删除推荐动作。 */
    public void deleteByRunCodes(Collection<String> runCodes) {
        if (runCodes == null || runCodes.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<AgentActionEntity> query = new LambdaQueryWrapper<AgentActionEntity>()
                .in(AgentActionEntity::getRunCode, runCodes);
        mapper.delete(query);
    }
}
