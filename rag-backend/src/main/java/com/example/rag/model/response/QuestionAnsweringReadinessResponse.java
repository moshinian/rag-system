package com.example.rag.model.response;

/** 问答链路就绪度返回对象。 */
public record QuestionAnsweringReadinessResponse(
        String knowledgeBaseCode,
        String knowledgeBaseStatus,
        boolean questionAnsweringReady,
        String embeddingProvider,
        String embeddingModel,
        String activeEmbeddingModel,
        Integer embeddingVectorDimensions,
        String vectorStore,
        Integer defaultTopK,
        long indexedChunkCount,
        long embeddedChunkCount,
        boolean reembedRequired,
        boolean reembedInProgress,
        Long currentRebuildRunId,
        String nextStep
) {
}
