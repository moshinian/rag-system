package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.AgentActionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * Agent 推荐动作 Mapper。
 */
public interface AgentActionMapper extends BaseMapper<AgentActionEntity> {
    @Update("""
            UPDATE agent_action
            SET status = 'EXECUTING', confirmed_by = #{operator}, confirmed_at = #{now},
                error_message = NULL, updated_at = #{now}
            WHERE action_code = #{actionCode}
              AND run_code = #{runCode}
              AND status = 'PENDING_CONFIRMATION'
              AND requires_confirmation = TRUE
            """)
    int claimForExecution(@Param("runCode") String runCode,
                          @Param("actionCode") String actionCode,
                          @Param("operator") String operator,
                          @Param("now") OffsetDateTime now);
}
