package com.example.rag.repository;

import com.example.rag.model.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Optional<DocumentEntity> findByDocumentCode(String documentCode);

    boolean existsByKnowledgeBaseIdAndContentHash(Long knowledgeBaseId, String contentHash);
}
