package com.example.rag.repository;

import com.example.rag.model.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 文档切块访问层。
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    /**
     * 按顺序读取某篇文档的全部 chunk。
     */
    List<DocumentChunkEntity> findByDocument_IdOrderByChunkIndexAsc(Long documentId);

    /**
     * 删除某篇文档的旧 chunk，便于重新处理。
     */
    void deleteByDocument_Id(Long documentId);
}
