package com.example.rag.model.response;

import java.util.List;

/**
 * Day 11 第一版问答返回对象。
 */
public record QaAnswerResponse(
        String question,
        String answer,
        Integer topK,
        String chatModel,
        List<RetrievedChunkResponse> retrievalResults,
        List<QaSourceResponse> sources
) {
}
