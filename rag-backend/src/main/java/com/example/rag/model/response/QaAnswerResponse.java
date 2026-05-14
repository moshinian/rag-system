package com.example.rag.model.response;

import com.example.rag.model.enums.RetrievalMode;

import java.util.List;

/** 问答结果返回对象。 */
public record QaAnswerResponse(
        String question,
        String answer,
        Integer topK,
        String chatModel,
        RetrievalMode retrievalMode,
        String fusionStrategy,
        List<RetrievedChunkResponse> retrievalResults,
        List<QaSourceResponse> sources
) {
}
