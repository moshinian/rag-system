package com.example.rag.repository;

import com.example.rag.model.entity.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 知识库访问层。
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    /** 按知识库编码查询。 */
    Optional<KnowledgeBaseEntity> findByKbCode(String kbCode);
}
