package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.model.dto.QaHistoryRecordView;
import com.example.rag.persistence.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 问答消息 Mapper。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    @Select("""
            SELECT cs.session_code AS sessionCode,
                   cs.session_name AS sessionName,
                   cm.message_code AS messageCode,
                   cm.question,
                   cm.answer,
                   cm.model_name AS modelName,
                   cm.top_k AS topK,
                   cm.latency_ms AS latencyMs,
                   cm.retrieved_chunks AS retrievedChunks,
                   cm.sources,
                   cm.prompt_template AS promptTemplate,
                   cm.created_at AS createdAt
            FROM chat_message cm
            JOIN chat_session cs ON cs.id = cm.session_id
            WHERE cs.knowledge_base_id = #{knowledgeBaseId}
            ORDER BY cm.created_at DESC, cm.id DESC
            """)
    IPage<QaHistoryRecordView> selectPageByKnowledgeBaseId(Page<QaHistoryRecordView> page,
                                                           @Param("knowledgeBaseId") Long knowledgeBaseId);
}
