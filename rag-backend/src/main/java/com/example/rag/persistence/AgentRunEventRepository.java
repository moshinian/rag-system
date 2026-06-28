package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.mapper.AgentRunEventMapper;
import com.example.rag.persistence.entity.AgentRunEventEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 运行事件访问层。
 */
@Repository
@SuppressWarnings("null") // MyBatis-Plus lambda 字段引用会被 JDT 误判为 NonNull 泛型转换告警。
public class AgentRunEventRepository {
    private final AgentRunEventMapper mapper;

    /** 构造 AgentRunEventRepository。 */
    public AgentRunEventRepository(AgentRunEventMapper mapper) {
        this.mapper = mapper;
    }

    /** 幂等插入事件，返回是否实际写入。 */
    public boolean insertIgnore(AgentRunEventEntity entity) {
        return mapper.insertIgnore(entity) == 1;
    }

    /** 按事件业务编码查询。 */
    public Optional<AgentRunEventEntity> findByEventCode(String eventCode) {
        LambdaQueryWrapper<AgentRunEventEntity> query = new LambdaQueryWrapper<AgentRunEventEntity>()
                .eq(AgentRunEventEntity::getEventCode, eventCode);
        return Optional.ofNullable(mapper.selectOne(query));
    }

    /** 按数据库主键查询。 */
    public Optional<AgentRunEventEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 查询 run 的全部事件，严格按数据库 id 排序。 */
    public List<AgentRunEventEntity> findByRunCode(String runCode) {
        return findAfterId(runCode, null);
    }

    /** 查询指定数据库 id 之后的事件。 */
    public List<AgentRunEventEntity> findAfterId(String runCode, Long lastDatabaseId) {
        LambdaQueryWrapper<AgentRunEventEntity> query = new LambdaQueryWrapper<AgentRunEventEntity>()
                .eq(AgentRunEventEntity::getRunCode, runCode)
                .gt(lastDatabaseId != null, AgentRunEventEntity::getId, lastDatabaseId)
                .orderByAsc(AgentRunEventEntity::getId);
        return mapper.selectList(query);
    }
}
