package com.example.rag.model.response;

import java.util.List;

/** 问题检索返回对象。 */
public record QuestionRetrievalResponse(
        String knowledgeBaseCode,
        String question,
        String embeddingModel,
        Integer topK,
        int hitCount,
        List<RetrievedChunkResponse> chunks
) {
}
