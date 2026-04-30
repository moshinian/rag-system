package com.example.rag.model.entity;

import com.example.rag.model.enums.DocumentChunkStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 文档切块实体。
 *
 * 该实体是后续向量化、召回和引用来源展示的基础数据来源。
 */
@Getter
@Setter
@Entity
@Table(name = "document_chunk")
public class DocumentChunkEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    private KnowledgeBaseEntity knowledgeBase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_type", nullable = false, length = 32)
    private String chunkType;

    @Column(length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_length", nullable = false)
    private Integer contentLength;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @Column(name = "start_offset", nullable = false)
    private Integer startOffset;

    @Column(name = "end_offset", nullable = false)
    private Integer endOffset;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    // 当前阶段只区分可用和禁用，后续可再扩展更多状态。
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentChunkStatus status = DocumentChunkStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        // chunk 首次入库时同步写入时间戳。
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        // chunk 更新时自动刷新更新时间。
        updatedAt = OffsetDateTime.now();
    }
}
