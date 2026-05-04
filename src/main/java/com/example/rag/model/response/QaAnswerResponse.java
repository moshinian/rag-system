package com.example.rag.model.response;

import java.util.List;

/** 问答结果返回对象。 */
public record QaAnswerResponse(
        String question,
        String answer,
        Integer topK,
        String chatModel,
        List<RetrievedChunkResponse> retrievalResults,
        List<QaSourceResponse> sources
) {
}
