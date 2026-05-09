package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.mapper.ChatMessageMapper;
import com.example.rag.model.dto.QaHistoryRecordView;
import com.example.rag.persistence.entity.ChatMessageEntity;
import com.example.rag.persistence.query.PageResult;
import com.example.rag.persistence.query.QaHistoryPageQuery;
import org.springframework.stereotype.Repository;

/**
 * 问答消息持久化访问层。
 */
@Repository
public class ChatMessageRepository {

    private final ChatMessageMapper chatMessageMapper;

    public ChatMessageRepository(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    /** 新增问答消息。 */
    public ChatMessageEntity insert(ChatMessageEntity entity) {
        chatMessageMapper.insert(entity);
        return entity;
    }

    /** 按知识库分页查询问答历史。 */
    public PageResult<QaHistoryRecordView> pageByKnowledgeBase(QaHistoryPageQuery pageQuery) {
        Page<QaHistoryRecordView> page = new Page<>(pageQuery.pageNo(), pageQuery.pageSize());
        IPage<QaHistoryRecordView> result = chatMessageMapper.selectPageByKnowledgeBaseId(
                page,
                pageQuery.knowledgeBaseId()
        );
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
