package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.rag.model.enums.IndexingTaskStage;
import com.example.rag.model.enums.IndexingTaskStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 文档处理任务持久化对象。
 *
 * 用于独立记录一次解析和切块执行的结果。
 */
@Getter
@Setter
@TableName("indexing_task")
public class IndexingTaskEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("document_id")
    private Long documentId;

    @TableField("task_type")
    private String taskType;

    private IndexingTaskStatus status = IndexingTaskStatus.QUEUED;

    @TableField("task_stage")
    private IndexingTaskStage taskStage = IndexingTaskStage.QUEUED;

    @TableField("parser_name")
    private String parserName;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("embedded_chunk_count")
    private Integer embeddedChunkCount = 0;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableField("created_by")
    private String createdBy = "system";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
