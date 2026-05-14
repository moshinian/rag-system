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
            SELECT COUNT(*)
            FROM document_chunk dc
            JOIN document d ON d.id = dc.document_id
            WHERE dc.knowledge_base_id = #{knowledgeBaseId}
              AND dc.status = 'ACTIVE'
              AND d.status = 'INDEXED'
            """)
    long countAvailableIndexedChunks(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("""
            SELECT COUNT(*)
            FROM document_chunk dc
            JOIN document d ON d.id = dc.document_id
            WHERE dc.knowledge_base_id = #{knowledgeBaseId}
              AND dc.status = 'ACTIVE'
              AND dc.embedding_status = 'EMBEDDED'
              AND dc.embedding_vector IS NOT NULL
              AND d.status = 'INDEXED'
            """)
    long countAvailableEmbeddedChunks(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("""
            SELECT COUNT(*)
            FROM document_chunk dc
            JOIN document d ON d.id = dc.document_id
            WHERE dc.knowledge_base_id = #{knowledgeBaseId}
              AND dc.status = 'ACTIVE'
              AND dc.embedding_status = 'EMBEDDED'
              AND dc.embedding_vector IS NOT NULL
              AND vector_dims(dc.embedding_vector) <> #{expectedDimensions}
              AND d.status = 'INDEXED'
            """)
    long countEmbeddedChunksWithDifferentDimensions(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                    @Param("expectedDimensions") int expectedDimensions);
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM document_chunk dc
                JOIN document d ON d.id = dc.document_id
                JOIN knowledge_base kb ON kb.id = dc.knowledge_base_id
                WHERE dc.status = 'ACTIVE'
                  AND dc.embedding_status = 'EMBEDDED'
                  AND dc.embedding_vector IS NOT NULL
                  AND d.status = 'INDEXED'
                  AND kb.status = 'ACTIVE'
                  AND (
                      COALESCE(dc.embedding_profile_fingerprint, '') <> #{currentFingerprint}
                      OR vector_dims(dc.embedding_vector) <> #{expectedDimensions}
                  )
            )
            """)
    boolean existsEmbeddedChunksNeedingRebuild(@Param("currentFingerprint") String currentFingerprint,
                                               @Param("expectedDimensions") int expectedDimensions);
    @Select("""
            SELECT dc.embedding_model
            FROM document_chunk dc
            JOIN document d ON d.id = dc.document_id
            JOIN knowledge_base kb ON kb.id = dc.knowledge_base_id
            WHERE dc.status = 'ACTIVE'
              AND dc.embedding_status = 'EMBEDDED'
              AND dc.embedding_vector IS NOT NULL
              AND d.status = 'INDEXED'
              AND kb.status = 'ACTIVE'
              AND dc.embedding_model IS NOT NULL
              AND dc.embedding_model <> ''
            ORDER BY dc.embedding_updated_at DESC NULLS LAST, dc.updated_at DESC, dc.id DESC
            LIMIT 1
            """)
    String findLatestEmbeddedModelInActiveKnowledgeBases();

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
    @Select({
            "<script>",
            "SELECT dc.id,",
            "       dc.document_id AS documentId,",
            "       d.document_code AS documentCode,",
            "       COALESCE(d.display_name, d.file_name) AS documentName,",
            "       dc.chunk_index AS chunkIndex,",
            "       dc.chunk_type AS chunkType,",
            "       dc.content,",
            "       dc.start_offset AS startOffset,",
            "       dc.end_offset AS endOffset,",
            "       dc.embedding_model AS embeddingModel,",
            "       CAST((",
            "           CASE WHEN LOWER(dc.content) LIKE CONCAT('%', LOWER(#{questionPhrase}), '%') THEN 1 ELSE 0 END",
            "           <foreach collection='terms' item='term'>",
            "               + CASE WHEN LOWER(dc.content) LIKE CONCAT('%', LOWER(#{term}), '%') THEN 1 ELSE 0 END",
            "           </foreach>",
            "       ) AS DOUBLE PRECISION) AS score",
            "FROM document_chunk dc",
            "JOIN document d ON d.id = dc.document_id",
            "WHERE dc.knowledge_base_id = #{knowledgeBaseId}",
            "  AND dc.status = 'ACTIVE'",
            "  AND dc.embedding_status = 'EMBEDDED'",
            "  AND dc.embedding_vector IS NOT NULL",
            "  AND d.status = 'INDEXED'",
            "  AND (LOWER(dc.content) LIKE CONCAT('%', LOWER(#{questionPhrase}), '%')",
            "       <if test='terms != null and terms.size() > 0'>",
            "           OR <foreach collection='terms' item='term' separator=' OR '>",
            "               LOWER(dc.content) LIKE CONCAT('%', LOWER(#{term}), '%')",
            "           </foreach>",
            "       </if>)",
            "ORDER BY score DESC, dc.chunk_index ASC, dc.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<RetrievedChunkCandidate> findTopKeywordChunks(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                       @Param("questionPhrase") String questionPhrase,
                                                       @Param("terms") List<String> terms,
                                                       @Param("limit") int limit);
    @Update("""
            UPDATE document_chunk
            SET embedding_status = #{embeddingStatus},
                embedding_model = #{embeddingModel},
                embedding_provider = #{embeddingProvider},
                embedding_profile_fingerprint = #{embeddingProfileFingerprint},
                embedding_rebuild_run_id = #{embeddingRebuildRunId},
                embedding_updated_by = #{embeddingUpdatedBy},
                embedding_error_message = #{embeddingErrorMessage},
                embedding_updated_at = #{embeddingUpdatedAt},
                updated_at = #{embeddingUpdatedAt}
            WHERE id = #{id}
            """)
    int updateEmbeddingState(@Param("id") Long id,
                             @Param("embeddingStatus") String embeddingStatus,
                             @Param("embeddingModel") String embeddingModel,
                             @Param("embeddingProvider") String embeddingProvider,
                             @Param("embeddingProfileFingerprint") String embeddingProfileFingerprint,
                             @Param("embeddingRebuildRunId") Long embeddingRebuildRunId,
                             @Param("embeddingUpdatedBy") String embeddingUpdatedBy,
                             @Param("embeddingErrorMessage") String embeddingErrorMessage,
                             @Param("embeddingUpdatedAt") OffsetDateTime embeddingUpdatedAt);
    @Update("""
            UPDATE document_chunk
            SET embedding_status = #{embeddingStatus},
                embedding_model = #{embeddingModel},
                embedding_provider = #{embeddingProvider},
                embedding_profile_fingerprint = #{embeddingProfileFingerprint},
                embedding_rebuild_run_id = #{embeddingRebuildRunId},
                embedding_updated_by = #{embeddingUpdatedBy},
                embedding_error_message = NULL,
                embedding_vector = CAST(#{embeddingVectorLiteral} AS vector),
                embedding_updated_at = #{embeddingUpdatedAt},
                updated_at = #{embeddingUpdatedAt}
            WHERE id = #{id}
            """)
    int updateEmbeddingVector(@Param("id") Long id,
                              @Param("embeddingStatus") String embeddingStatus,
                              @Param("embeddingModel") String embeddingModel,
                              @Param("embeddingProvider") String embeddingProvider,
                              @Param("embeddingProfileFingerprint") String embeddingProfileFingerprint,
                              @Param("embeddingRebuildRunId") Long embeddingRebuildRunId,
                              @Param("embeddingUpdatedBy") String embeddingUpdatedBy,
                              @Param("embeddingVectorLiteral") String embeddingVectorLiteral,
                              @Param("embeddingUpdatedAt") OffsetDateTime embeddingUpdatedAt);
    @Update("""
            UPDATE document_chunk dc
            SET embedding_status = 'PENDING',
                embedding_model = #{embeddingModel},
                embedding_provider = #{embeddingProvider},
                embedding_profile_fingerprint = #{embeddingProfileFingerprint},
                embedding_rebuild_run_id = #{embeddingRebuildRunId},
                embedding_updated_by = #{embeddingUpdatedBy},
                embedding_error_message = NULL,
                embedding_vector = NULL,
                embedding_updated_at = #{embeddingUpdatedAt},
                updated_at = #{embeddingUpdatedAt}
            FROM document d
            JOIN knowledge_base kb ON kb.id = d.knowledge_base_id
            WHERE dc.document_id = d.id
              AND dc.status = 'ACTIVE'
              AND d.status = 'INDEXED'
              AND kb.status = 'ACTIVE'
            """)
    int resetEmbeddingsForRebuild(@Param("embeddingModel") String embeddingModel,
                                  @Param("embeddingProvider") String embeddingProvider,
                                  @Param("embeddingProfileFingerprint") String embeddingProfileFingerprint,
                                  @Param("embeddingRebuildRunId") Long embeddingRebuildRunId,
                                  @Param("embeddingUpdatedBy") String embeddingUpdatedBy,
                                  @Param("embeddingUpdatedAt") OffsetDateTime embeddingUpdatedAt);
}
