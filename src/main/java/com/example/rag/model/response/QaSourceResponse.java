package com.example.rag.model.response;

/** 问答来源返回对象。 */
public record QaSourceResponse(
        String documentCode,
        String documentName,
        Long chunkId,
        Integer chunkIndex,
        String content,
        Double score,
        Integer startOffset,
        Integer endOffset
) {
}
