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
 * 问答消息持久化对象。
 */
@Getter
@Setter
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("message_code")
    private String messageCode;

    @TableField("session_id")
    private Long sessionId;

    @TableField("message_type")
    private String messageType;

    private String question;

    private String answer;

    @TableField("retrieved_chunks")
    private String retrievedChunks;

    private String sources;

    @TableField("prompt_template")
    private String promptTemplate;

    @TableField("model_name")
    private String modelName;

    @TableField("top_k")
    private Integer topK;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
