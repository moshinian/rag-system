package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.DocumentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档 Mapper。
 */
public interface DocumentMapper extends BaseMapper<DocumentEntity> {

    /**
     * 按知识库编码和文档编码查询文档。
     */
    @Select("""
            SELECT d.*
            FROM document d
            JOIN knowledge_base kb ON kb.id = d.knowledge_base_id
            WHERE d.document_code = #{documentCode}
              AND kb.kb_code = #{kbCode}
            LIMIT 1
            """)
    DocumentEntity selectByCodeInKnowledgeBase(@Param("documentCode") String documentCode,
                                               @Param("kbCode") String kbCode);
}
