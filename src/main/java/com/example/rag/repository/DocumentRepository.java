package com.example.rag.repository;

import com.example.rag.model.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 文档主表访问层。
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    /**
     * 按文档编码查询。
     */
    Optional<DocumentEntity> findByDocumentCode(String documentCode);

    /**
     * 同时按知识库编码和文档编码查询，避免跨知识库误命中。
     */
    Optional<DocumentEntity> findByDocumentCodeAndKnowledgeBase_KbCode(String documentCode, String kbCode);

    /**
     * 基于知识库和内容摘要做基础防重。
     */
    boolean existsByKnowledgeBaseIdAndContentHash(Long knowledgeBaseId, String contentHash);
}
