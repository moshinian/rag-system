package com.example.rag.model.response;

import com.example.rag.model.enums.RetrievalMode;

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
        List<RetrievedChunkResponse> chunks
) {
}
