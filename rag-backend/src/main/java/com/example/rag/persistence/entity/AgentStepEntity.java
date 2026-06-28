package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Agent 节点或工具调用轨迹记录。
 */
@Getter
@Setter
@TableName("agent_step")
public class AgentStepEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("run_code")
    private String runCode;

    @TableField("node_invocation_id")
    private String nodeInvocationId;

    @TableField("step_code")
    private String stepCode;

    @TableField("node_name")
    private String nodeName;

    @TableField("tool_name")
    private String toolName;

    @TableField("step_type")
    private AgentStepType stepType = AgentStepType.NODE;

    private AgentStepStatus status = AgentStepStatus.PENDING;

    @TableField("input_json")
    private String inputJson;

    @TableField("output_json")
    private String outputJson;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
