package com.example.rag.model.entity;

import com.example.rag.model.enums.KnowledgeBaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 知识库主表实体。
 */
@Getter
@Setter
@Entity
@Table(name = "knowledge_base")
public class KnowledgeBaseEntity {

    @Id
    private Long id;

    @Column(name = "kb_code", nullable = false, unique = true, length = 64)
    private String kbCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeBaseStatus status = KnowledgeBaseStatus.ACTIVE;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        // 首次创建时补齐时间字段。
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        // 更新时只刷新更新时间。
        updatedAt = OffsetDateTime.now();
    }
}
