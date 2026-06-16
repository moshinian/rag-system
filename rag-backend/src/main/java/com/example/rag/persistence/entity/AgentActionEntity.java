package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Agent 推荐动作与确认执行记录。
 */
@Getter
@Setter
@TableName("agent_action")
public class AgentActionEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("run_code")
    private String runCode;

    @TableField("action_code")
    private String actionCode;

    @TableField("tool_name")
    private String toolName;

    private String title;
    private String reason;

    @TableField("risk_level")
    private AgentActionRiskLevel riskLevel = AgentActionRiskLevel.MEDIUM;

    @TableField("requires_confirmation")
    private Boolean requiresConfirmation = true;

    private AgentActionStatus status = AgentActionStatus.PENDING_CONFIRMATION;

    @TableField("action_payload")
    private String actionPayload;

    @TableField("confirmed_by")
    private String confirmedBy;

    @TableField("confirmed_at")
    private OffsetDateTime confirmedAt;

    @TableField("executed_at")
    private OffsetDateTime executedAt;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
