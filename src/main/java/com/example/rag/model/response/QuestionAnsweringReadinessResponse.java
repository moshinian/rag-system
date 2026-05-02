package com.example.rag.model.response;

/**
 * Week 2 问答链路就绪度返回对象。
 */
public record QuestionAnsweringReadinessResponse(
        String knowledgeBaseCode,
        String knowledgeBaseStatus,
        boolean questionAnsweringReady,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingVectorDimensions,
        String vectorStore,
        Integer defaultTopK,
        long indexedChunkCount,
        long embeddedChunkCount,
        String nextStep
) {
}
