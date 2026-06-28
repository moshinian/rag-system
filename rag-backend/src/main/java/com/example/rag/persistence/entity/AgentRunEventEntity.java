package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.rag.model.enums.AgentRunEventType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Java 规范化后的 Agent 运行事件。
 */
@Getter
@Setter
@TableName("agent_run_event")
public class AgentRunEventEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("event_code")
    private String eventCode;

    @TableField("run_code")
    private String runCode;

    @TableField("node_invocation_id")
    private String nodeInvocationId;

    @TableField("event_type")
    private AgentRunEventType eventType;

    @TableField("node_name")
    private String nodeName;

    @TableField("tool_name")
    private String toolName;

    private String status;
    private String message;

    @TableField("payload_json")
    private String payloadJson;

    private Boolean terminal = false;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
