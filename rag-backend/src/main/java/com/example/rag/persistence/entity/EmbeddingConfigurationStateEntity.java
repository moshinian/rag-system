package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Embedding 配置状态单例表。
 */
@Getter
@Setter
@TableName("embedding_configuration_state")
public class EmbeddingConfigurationStateEntity {

    @TableId
    private Long id;

    @TableField("current_config_fingerprint")
    private String currentConfigFingerprint;

    @TableField("active_config_fingerprint")
    private String activeConfigFingerprint;

    @TableField("active_embedding_model")
    private String activeEmbeddingModel;

    @TableField("reembed_required")
    private Boolean reembedRequired;

    @TableField(value = "rebuild_run_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long rebuildRunId;

    @TableField("reembed_confirmed_by")
    private String reembedConfirmedBy;

    @TableField("reembed_confirmed_at")
    private OffsetDateTime reembedConfirmedAt;

    @TableField("reembed_started_at")
    private OffsetDateTime reembedStartedAt;

    @TableField(value = "reembed_finished_at", updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime reembedFinishedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
