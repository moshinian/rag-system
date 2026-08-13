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

    @Select("""
            WITH candidate AS (
                SELECT id FROM agent_run
                WHERE status = 'QUEUED'
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE agent_run run
            SET status = 'RUNNING', owner_instance_id = #{ownerInstanceId},
                claimed_at = #{now}, runtime_heartbeat_at = #{now}, lease_until = #{leaseUntil},
                lease_version = lease_version + 1, attempt_count = attempt_count + 1,
                error_message = NULL, updated_at = #{now}
            FROM candidate
            WHERE run.id = candidate.id
            RETURNING run.*
            """)
    AgentRunEntity claimNext(@Param("ownerInstanceId") String ownerInstanceId,
                             @Param("now") OffsetDateTime now,
                             @Param("leaseUntil") OffsetDateTime leaseUntil);

    @Update("""
            UPDATE agent_run
            SET runtime_heartbeat_at = #{now}, lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE run_code = #{runCode}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int heartbeatOwned(@Param("runCode") String runCode,
                       @Param("ownerInstanceId") String ownerInstanceId,
                       @Param("leaseVersion") Long leaseVersion,
                       @Param("now") OffsetDateTime now,
                       @Param("leaseUntil") OffsetDateTime leaseUntil);

    @Select("""
            SELECT * FROM agent_run
            WHERE run_code = #{runCode}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            FOR UPDATE
            """)
    AgentRunEntity lockOwned(@Param("runCode") String runCode,
                             @Param("ownerInstanceId") String ownerInstanceId,
                             @Param("leaseVersion") Long leaseVersion);

    @Select("""
            SELECT * FROM agent_run
            WHERE status = 'RUNNING' AND lease_until < #{now}
            ORDER BY lease_until, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """)
    AgentRunEntity lockNextExpired(@Param("now") OffsetDateTime now);

    @Update("""
            UPDATE agent_run
            SET status = 'QUEUED', owner_instance_id = NULL, claimed_at = NULL,
                lease_until = NULL, runtime_heartbeat_at = NULL,
                error_message = #{errorMessage}, updated_at = #{now}
            WHERE run_code = #{runCode}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int returnOwnedToQueue(@Param("runCode") String runCode,
                           @Param("ownerInstanceId") String ownerInstanceId,
                           @Param("leaseVersion") Long leaseVersion,
                           @Param("errorMessage") String errorMessage,
                           @Param("now") OffsetDateTime now);

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
