package com.example.rag.model.response;

import com.example.rag.model.enums.RetrievalMode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 问答历史记录返回对象。
 */
public record QaHistoryRecordResponse(
        String sessionCode,
        String sessionName,
        String messageCode,
        String question,
        String answer,
        String chatModel,
        Integer topK,
        RetrievalMode retrievalMode,
        String fusionStrategy,
        Long latencyMs,
        String promptTemplate,
        List<RetrievedChunkResponse> retrievalResults,
        List<QaSourceResponse> sources,
        OffsetDateTime createdAt
) {
}
