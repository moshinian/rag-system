package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 检索命中的 chunk 结果。 */
public record RetrievedChunkResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long chunkId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long documentId,
        String documentCode,
        String documentName,
        Integer chunkIndex,
        String chunkType,
        String content,
        Integer startOffset,
        Integer endOffset,
        String embeddingModel,
        Double score
) {
}
