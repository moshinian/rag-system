package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Agent 运行记录 Mapper。
 */
public interface AgentRunMapper extends BaseMapper<AgentRunEntity> {

    /** 使用数据库时间更新 Runtime heartbeat，避免应用时间与数据库时间偏移。 */
    @Update("""
            UPDATE agent_run
            SET runtime_heartbeat_at = now(),
                updated_at = now()
            WHERE run_code = #{runCode}
              AND status = 'RUNNING'
            """)
    int updateRuntimeHeartbeatToNow(@Param("runCode") String runCode);

    /** 查询满足 Recovery 条件的 RUNNING run。 */
    @Select("""
            SELECT ar.*
            FROM agent_run ar
            WHERE ar.status = 'RUNNING'
              AND ar.created_at < #{runningCutoff}
              AND (ar.runtime_heartbeat_at IS NULL OR ar.runtime_heartbeat_at < #{idleCutoff})
              AND NOT EXISTS (
                  SELECT 1
                  FROM agent_run_event terminal_event
                  WHERE terminal_event.run_code = ar.run_code
                    AND terminal_event.terminal = TRUE
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM agent_run_event recent_event
                  WHERE recent_event.run_code = ar.run_code
                    AND recent_event.created_at >= #{idleCutoff}
              )
            ORDER BY ar.created_at ASC, ar.id ASC
            LIMIT #{limit}
            """)
    List<AgentRunEntity> findRecoverableRunningRuns(@Param("runningCutoff") OffsetDateTime runningCutoff,
                                                    @Param("idleCutoff") OffsetDateTime idleCutoff,
                                                    @Param("limit") int limit);

    /** 条件化标记 Recovery 失败；scan 后若已有 heartbeat 或 terminal event，则不会更新。 */
    @Update("""
            UPDATE agent_run ar
            SET status = 'FAILED',
                error_message = #{errorMessage},
                finished_at = now(),
                updated_at = now()
            WHERE ar.run_code = #{runCode}
              AND ar.status = 'RUNNING'
              AND (ar.runtime_heartbeat_at IS NULL OR ar.runtime_heartbeat_at < #{idleCutoff})
              AND NOT EXISTS (
                  SELECT 1
                  FROM agent_run_event terminal_event
                  WHERE terminal_event.run_code = ar.run_code
                    AND terminal_event.terminal = TRUE
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM agent_run_event recent_event
                  WHERE recent_event.run_code = ar.run_code
                    AND recent_event.created_at >= #{idleCutoff}
              )
            """)
    int markRunningRunFailedByRecovery(@Param("runCode") String runCode,
                                       @Param("idleCutoff") OffsetDateTime idleCutoff,
                                       @Param("errorMessage") String errorMessage);
}
