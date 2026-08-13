package com.example.rag.model.response;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.enums.RerankStatus;

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
        RerankStatus rerankStatus,
        String rerankModel,
        int rerankCandidateCount,
        long rerankDurationMs,
        Long latencyMs,
        String promptTemplate,
        List<RetrievedChunkResponse> retrievalResults,
        List<QaSourceResponse> sources,
        OffsetDateTime createdAt
) {
    /** 兼容未接入重排序前的历史返回构造调用。 */
    public QaHistoryRecordResponse(String sessionCode,
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
                                   OffsetDateTime createdAt) {
        this(sessionCode, sessionName, messageCode, question, answer, chatModel, topK,
                retrievalMode, fusionStrategy, RerankStatus.DISABLED, null, 0, 0L,
                latencyMs, promptTemplate, retrievalResults, sources, createdAt);
    }
}
