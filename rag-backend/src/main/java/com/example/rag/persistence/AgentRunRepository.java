package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.AgentRunMapper;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agent 运行记录访问层。
 */
@Repository
@SuppressWarnings("null") // MyBatis-Plus lambda 字段引用会被 JDT 误判为 NonNull 泛型转换告警。
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

    /** 使用数据库时间更新 Runtime heartbeat，仅 RUNNING run 会被更新。 */
    public int updateRuntimeHeartbeatToNow(String runCode) {
        return mapper.updateRuntimeHeartbeatToNow(runCode);
    }

    /** 查询可被 Recovery 收敛的 RUNNING run。 */
    public List<AgentRunEntity> findRecoverableRunningRuns(OffsetDateTime runningCutoff,
                                                           OffsetDateTime idleCutoff,
                                                           int limit) {
        return mapper.findRecoverableRunningRuns(runningCutoff, idleCutoff, Math.max(1, limit));
    }

    /** 条件化 Recovery 失败更新；返回 1 表示当前线程赢得恢复权。 */
    public int markRunningRunFailedByRecovery(String runCode,
                                              OffsetDateTime idleCutoff,
                                              String errorMessage) {
        return mapper.markRunningRunFailedByRecovery(runCode, idleCutoff, errorMessage);
    }
}
