package com.example.rag.persistence;

import com.example.rag.mapper.ChatSessionMapper;
import com.example.rag.persistence.entity.ChatSessionEntity;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

/**
 * 问答会话持久化访问层。
 */
@Repository
public class ChatSessionRepository {
    private final ChatSessionMapper chatSessionMapper;

    /** 构造ChatSessionRepository。 */
    public ChatSessionRepository(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    /** 新增问答会话。 */
    public ChatSessionEntity insert(ChatSessionEntity entity) {
        chatSessionMapper.insert(entity);
        return entity;
    }

    /** 按知识库读取会话 ID，供级联删除消息使用。 */
    public List<Long> findIdsByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<ChatSessionEntity> query = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByAsc(ChatSessionEntity::getId)
                .select(ChatSessionEntity::getId);
        return chatSessionMapper.selectList(query).stream()
                .map(ChatSessionEntity::getId)
                .toList();
    }

    /** 按知识库删除全部会话。 */
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<ChatSessionEntity> query = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getKnowledgeBaseId, knowledgeBaseId);
        chatSessionMapper.delete(query);
    }
}
