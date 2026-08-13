package com.example.rag.model.response;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.enums.RerankStatus;

import java.util.List;

/** 问答结果返回对象。 */
public record QaAnswerResponse(
        String question,
        String answer,
        Integer topK,
        String chatModel,
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
        long llmDurationMs,
        long totalDurationMs,
        List<RetrievedChunkResponse> retrievalResults,
        List<QaSourceResponse> sources
) {
    /** 兼容未接入重排序前的构造调用。 */
    public QaAnswerResponse(String question,
                            String answer,
                            Integer topK,
                            String chatModel,
                            RetrievalMode retrievalMode,
                            String fusionStrategy,
                            int denseHitCount,
                            int keywordHitCount,
                            int hitCount,
                            long denseDurationMs,
                            long keywordDurationMs,
                            long fusionDurationMs,
                            long llmDurationMs,
                            long totalDurationMs,
                            List<RetrievedChunkResponse> retrievalResults,
                            List<QaSourceResponse> sources) {
        this(question, answer, topK, chatModel, retrievalMode, fusionStrategy, denseHitCount,
                keywordHitCount, hitCount, denseDurationMs, keywordDurationMs, fusionDurationMs,
                RerankStatus.DISABLED, null, 0, 0L, llmDurationMs, totalDurationMs,
                retrievalResults, sources);
    }
}
