package com.example.rag.model.response;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.enums.RerankStatus;

import java.util.List;

/** 问题检索返回对象。 */
public record QuestionRetrievalResponse(
        String knowledgeBaseCode,
        String question,
        String embeddingModel,
        Integer topK,
        RetrievalMode retrievalMode,
        String fusionStrategy,
        int denseHitCount,
        int keywordHitCount,
        int hitCount,
        long denseDurationMs,
        long keywordDurationMs,
        long fusionDurationMs,
        RerankStatus rerankStatus,
        String rerankModel,
        int rerankCandidateCount,
        long rerankDurationMs,
        long totalDurationMs,
        List<RetrievedChunkResponse> chunks
) {
    /** 兼容未接入重排序前的构造调用。 */
    public QuestionRetrievalResponse(String knowledgeBaseCode,
                                     String question,
                                     String embeddingModel,
                                     Integer topK,
                                     RetrievalMode retrievalMode,
                                     String fusionStrategy,
                                     int denseHitCount,
                                     int keywordHitCount,
                                     int hitCount,
                                     long denseDurationMs,
                                     long keywordDurationMs,
                                     long fusionDurationMs,
                                     long totalDurationMs,
                                     List<RetrievedChunkResponse> chunks) {
        this(knowledgeBaseCode, question, embeddingModel, topK, retrievalMode, fusionStrategy,
                denseHitCount, keywordHitCount, hitCount, denseDurationMs, keywordDurationMs,
                fusionDurationMs, RerankStatus.DISABLED, null, 0, 0L, totalDurationMs, chunks);
    }
}
