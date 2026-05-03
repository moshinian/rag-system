package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.model.dto.RetrievedChunkCandidate;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文档切块 Mapper。
 */
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    @Select("""
            SELECT dc.id,
                   dc.document_id AS documentId,
                   d.document_code AS documentCode,
                   COALESCE(d.display_name, d.file_name) AS documentName,
                   dc.chunk_index AS chunkIndex,
                   dc.chunk_type AS chunkType,
                   dc.content,
                   dc.start_offset AS startOffset,
                   dc.end_offset AS endOffset,
                   dc.embedding_model AS embeddingModel,
                   1 - (dc.embedding_vector <=> CAST(#{queryVectorLiteral} AS vector)) AS score
            FROM document_chunk dc
            JOIN document d ON d.id = dc.document_id
            WHERE dc.knowledge_base_id = #{knowledgeBaseId}
              AND dc.status = 'ACTIVE'
              AND dc.embedding_status = 'EMBEDDED'
              AND dc.embedding_vector IS NOT NULL
              AND d.status = 'INDEXED'
            ORDER BY dc.embedding_vector <=> CAST(#{queryVectorLiteral} AS vector) ASC
            LIMIT #{topK}
            """)
    List<RetrievedChunkCandidate> findTopKSimilarChunks(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                        @Param("queryVectorLiteral") String queryVectorLiteral,
                                                        @Param("topK") int topK);

    @Update("""
            UPDATE document_chunk
            SET embedding_status = #{embeddingStatus},
                embedding_model = #{embeddingModel},
                embedding_error_message = #{embeddingErrorMessage},
                embedding_updated_at = #{embeddingUpdatedAt},
                updated_at = #{embeddingUpdatedAt}
            WHERE id = #{id}
            """)
    int updateEmbeddingState(@Param("id") Long id,
                             @Param("embeddingStatus") String embeddingStatus,
                             @Param("embeddingModel") String embeddingModel,
                             @Param("embeddingErrorMessage") String embeddingErrorMessage,
                             @Param("embeddingUpdatedAt") OffsetDateTime embeddingUpdatedAt);

    @Update("""
            UPDATE document_chunk
            SET embedding_status = #{embeddingStatus},
                embedding_model = #{embeddingModel},
                embedding_error_message = NULL,
                embedding_vector = CAST(#{embeddingVectorLiteral} AS vector),
                embedding_updated_at = #{embeddingUpdatedAt},
                updated_at = #{embeddingUpdatedAt}
            WHERE id = #{id}
            """)
    int updateEmbeddingVector(@Param("id") Long id,
                              @Param("embeddingStatus") String embeddingStatus,
                              @Param("embeddingModel") String embeddingModel,
                              @Param("embeddingVectorLiteral") String embeddingVectorLiteral,
                              @Param("embeddingUpdatedAt") OffsetDateTime embeddingUpdatedAt);
}
