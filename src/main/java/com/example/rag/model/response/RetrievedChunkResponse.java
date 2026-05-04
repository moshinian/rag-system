package com.example.rag.model.response;

/** 检索命中的 chunk 结果。 */
public record RetrievedChunkResponse(
        Long chunkId,
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
