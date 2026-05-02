package com.example.rag.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.DocumentChunkEntity;

import java.time.OffsetDateTime;

/**
 * 文档切块 Mapper。
 */
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

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
