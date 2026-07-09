package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.rag.model.enums.AgentRunStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 一次 Agent 诊断运行记录。
 */
@Getter
@Setter
@TableName("agent_run")
public class AgentRunEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("run_code")
    private String runCode;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    private String goal;
    private String question;

    private AgentRunStatus status = AgentRunStatus.RUNNING;
    private String summary;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private String createdBy = "system";

    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableField("runtime_heartbeat_at")
    private OffsetDateTime runtimeHeartbeatAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
