package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 问答会话持久化对象。
 */
@Getter
@Setter
@TableName("chat_session")
public class ChatSessionEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("session_code")
    private String sessionCode;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("session_name")
    private String sessionName;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
