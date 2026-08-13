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
        Double score,
        Double rerankScore
) {
    /** 兼容未接入重排序前的调用方。 */
    public RetrievedChunkResponse(Long chunkId,
                                  Long documentId,
                                  String documentCode,
                                  String documentName,
                                  Integer chunkIndex,
                                  String chunkType,
                                  String content,
                                  Integer startOffset,
                                  Integer endOffset,
                                  String embeddingModel,
                                  Double score) {
        this(chunkId, documentId, documentCode, documentName, chunkIndex, chunkType, content,
                startOffset, endOffset, embeddingModel, score, null);
    }
}
