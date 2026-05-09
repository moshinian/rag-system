package com.example.rag.persistence;

import com.example.rag.mapper.ChatSessionMapper;
import com.example.rag.persistence.entity.ChatSessionEntity;
import org.springframework.stereotype.Repository;

/**
 * 问答会话持久化访问层。
 */
@Repository
public class ChatSessionRepository {

    private final ChatSessionMapper chatSessionMapper;

    public ChatSessionRepository(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    /** 新增问答会话。 */
    public ChatSessionEntity insert(ChatSessionEntity entity) {
        chatSessionMapper.insert(entity);
        return entity;
    }
}
