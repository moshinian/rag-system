package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.AgentRunEventEntity;
import org.apache.ibatis.annotations.Insert;

/**
 * Agent 运行事件 Mapper。
 */
public interface AgentRunEventMapper extends BaseMapper<AgentRunEventEntity> {

    /**
     * 幂等插入事件；重复 eventCode 时返回 0。
     */
    @Insert("""
            INSERT INTO agent_run_event (
                id, event_code, run_code, node_invocation_id, event_type,
                node_name, tool_name, status, message, payload_json,
                terminal, created_at, updated_at
            ) VALUES (
                #{id}, #{eventCode}, #{runCode}, #{nodeInvocationId}, #{eventType},
                #{nodeName}, #{toolName}, #{status}, #{message}, #{payloadJson},
                #{terminal}, #{createdAt}, #{updatedAt}
            )
            ON CONFLICT (event_code) DO NOTHING
            """)
    int insertIgnore(AgentRunEventEntity entity);
}
