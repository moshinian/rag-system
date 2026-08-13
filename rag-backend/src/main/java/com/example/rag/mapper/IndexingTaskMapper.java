package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * 切块任务 Mapper。
 */
public interface IndexingTaskMapper extends BaseMapper<IndexingTaskEntity> {

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM indexing_task
                WHERE id = #{taskId}
                  AND status = 'RUNNING'
                  AND owner_instance_id = #{ownerInstanceId}
                  AND lease_version = #{leaseVersion}
                  AND lease_until > NOW()
            )
            """)
    boolean isOwned(@Param("taskId") Long taskId,
                    @Param("ownerInstanceId") String ownerInstanceId,
                    @Param("leaseVersion") Long leaseVersion);
    @Select("""
            WITH candidate AS (
                SELECT id
                FROM indexing_task
                WHERE task_type = #{taskType}
                  AND status = 'QUEUED'
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE indexing_task task
            SET status = 'RUNNING',
                task_stage = 'DOCUMENT_PROCESSING',
                owner_instance_id = #{ownerInstanceId},
                claimed_at = #{now},
                started_at = COALESCE(started_at, #{now}),
                last_heartbeat_at = #{now},
                lease_until = #{leaseUntil},
                lease_version = lease_version + 1,
                error_message = NULL,
                updated_at = #{now}
            FROM candidate
            WHERE task.id = candidate.id
            RETURNING task.*
            """)
    IndexingTaskEntity claimNext(@Param("taskType") String taskType,
                                 @Param("ownerInstanceId") String ownerInstanceId,
                                 @Param("now") OffsetDateTime now,
                                 @Param("leaseUntil") OffsetDateTime leaseUntil);

    @Update("""
            UPDATE indexing_task
            SET last_heartbeat_at = #{now}, lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int heartbeat(@Param("taskId") Long taskId,
                  @Param("ownerInstanceId") String ownerInstanceId,
                  @Param("leaseVersion") Long leaseVersion,
                  @Param("now") OffsetDateTime now,
                  @Param("leaseUntil") OffsetDateTime leaseUntil);

    @Update("""
            UPDATE indexing_task
            SET task_stage = #{stage}, parser_name = #{parserName}, chunk_count = #{chunkCount},
                last_heartbeat_at = #{now}, lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int updateOwnedStage(@Param("taskId") Long taskId,
                         @Param("ownerInstanceId") String ownerInstanceId,
                         @Param("leaseVersion") Long leaseVersion,
                         @Param("stage") String stage,
                         @Param("parserName") String parserName,
                         @Param("chunkCount") Integer chunkCount,
                         @Param("now") OffsetDateTime now,
                         @Param("leaseUntil") OffsetDateTime leaseUntil);

    @Update("""
            UPDATE indexing_task
            SET status = 'SUCCEEDED', task_stage = 'COMPLETED', embedded_chunk_count = #{embeddedChunkCount},
                finished_at = #{now}, last_heartbeat_at = #{now}, lease_until = NULL, updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int completeOwned(@Param("taskId") Long taskId,
                      @Param("ownerInstanceId") String ownerInstanceId,
                      @Param("leaseVersion") Long leaseVersion,
                      @Param("embeddedChunkCount") Integer embeddedChunkCount,
                      @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE indexing_task
            SET status = 'FAILED', error_message = #{errorMessage}, finished_at = #{now},
                last_heartbeat_at = #{now}, lease_until = NULL, updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int failOwned(@Param("taskId") Long taskId,
                  @Param("ownerInstanceId") String ownerInstanceId,
                  @Param("leaseVersion") Long leaseVersion,
                  @Param("errorMessage") String errorMessage,
                  @Param("now") OffsetDateTime now);

    @Select("""
            SELECT *
            FROM indexing_task
            WHERE task_type = #{taskType}
              AND status = 'RUNNING'
              AND lease_until < #{now}
            ORDER BY lease_until, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """)
    IndexingTaskEntity lockNextExpired(@Param("taskType") String taskType,
                                       @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE indexing_task
            SET status = 'QUEUED', task_stage = 'QUEUED', owner_instance_id = NULL,
                claimed_at = NULL, lease_until = NULL, last_heartbeat_at = NULL,
                error_message = #{errorMessage}, updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
              AND owner_instance_id = #{ownerInstanceId}
              AND lease_version = #{leaseVersion}
            """)
    int returnOwnedToQueue(@Param("taskId") Long taskId,
                           @Param("ownerInstanceId") String ownerInstanceId,
                           @Param("leaseVersion") Long leaseVersion,
                           @Param("errorMessage") String errorMessage,
                           @Param("now") OffsetDateTime now);
}
