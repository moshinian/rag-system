package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.rag.model.enums.EmbeddingRebuildRunStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 一次全量重嵌入运行记录。
 */
@Getter
@Setter
@TableName("embedding_rebuild_run")
public class EmbeddingRebuildRunEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private EmbeddingRebuildRunStatus status = EmbeddingRebuildRunStatus.QUEUED;

    @TableField("target_fingerprint")
    private String targetFingerprint;

    @TableField("target_model")
    private String targetModel;

    @TableField("target_provider")
    private String targetProvider;

    @TableField("vector_dimensions")
    private Integer vectorDimensions;

    @TableField("distance_metric")
    private String distanceMetric;

    @TableField("created_by")
    private String createdBy;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableField("total_document_count")
    private Integer totalDocumentCount;

    @TableField("succeeded_document_count")
    private Integer succeededDocumentCount;

    @TableField("failed_document_count")
    private Integer failedDocumentCount;

    @TableField("error_summary")
    private String errorSummary;

    @TableField("last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
