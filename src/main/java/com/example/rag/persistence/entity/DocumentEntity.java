package com.example.rag.persistence.entity;

import com.example.rag.model.enums.DocumentStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 文档主表持久化对象。
 *
 * 保存原始文档的元数据，不保存切块后的检索数据。
 */
@Getter
@Setter
@TableName("document")
public class DocumentEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("document_code")
    private String documentCode;

    @TableField("file_name")
    private String fileName;

    @TableField("display_name")
    private String displayName;

    @TableField("file_type")
    private String fileType;

    @TableField("media_type")
    private String mediaType;

    @TableField("storage_path")
    private String storagePath;

    @TableField("file_size")
    private Long fileSize;

    @TableField("content_hash")
    private String contentHash;

    // 文档当前处理阶段，例如 UPLOADED / PARSING / INDEXED。
    private DocumentStatus status = DocumentStatus.UPLOADED;

    private Integer version = 1;

    private String source;

    private String tags;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private String createdBy = "system";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
