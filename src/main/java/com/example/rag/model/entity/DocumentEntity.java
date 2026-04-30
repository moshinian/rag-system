package com.example.rag.model.entity;

import com.example.rag.model.enums.DocumentStatus;
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
 * 文档主表实体。
 *
 * 保存原始文档的元数据，不保存切块后的检索数据。
 */
@Getter
@Setter
@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    private KnowledgeBaseEntity knowledgeBase;

    @Column(name = "document_code", nullable = false, unique = true, length = 64)
    private String documentCode;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "file_type", nullable = false, length = 32)
    private String fileType;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    // 文档当前处理阶段，例如 UPLOADED / PARSING / INDEXED。
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(length = 256)
    private String source;

    @Column(length = 512)
    private String tags;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        // 创建时统一补齐创建时间和更新时间。
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        // 每次更新实体时自动刷新更新时间。
        updatedAt = OffsetDateTime.now();
    }
}
