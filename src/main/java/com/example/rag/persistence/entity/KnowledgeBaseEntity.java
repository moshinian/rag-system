package com.example.rag.persistence.entity;

import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 知识库主表持久化对象。
 */
@Getter
@Setter
@TableName("knowledge_base")
public class KnowledgeBaseEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("kb_code")
    private String kbCode;

    private String name;

    private String description;

    private KnowledgeBaseStatus status = KnowledgeBaseStatus.ACTIVE;

    @TableField("created_by")
    private String createdBy = "system";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
