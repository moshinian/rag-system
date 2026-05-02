package com.example.rag.persistence.entity;

import com.example.rag.model.enums.DocumentChunkStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 文档切块持久化对象。
 *
 * 该对象是后续向量化、召回和引用来源展示的基础数据来源。
 */
@Getter
@Setter
@TableName("document_chunk")
public class DocumentChunkEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("document_id")
    private Long documentId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("chunk_type")
    private String chunkType;

    private String title;

    private String content;

    @TableField("content_length")
    private Integer contentLength;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("start_offset")
    private Integer startOffset;

    @TableField("end_offset")
    private Integer endOffset;

    @TableField("metadata_json")
    private String metadataJson;

    // 当前阶段只区分可用和禁用，后续可再扩展更多状态。
    private DocumentChunkStatus status = DocumentChunkStatus.ACTIVE;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
