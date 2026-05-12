package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

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
        @JsonSerialize(using = ToStringSerializer.class)
        Long currentRebuildRunId,
        String nextStep
) {
}
