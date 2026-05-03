package com.example.rag.model.response;

/**
 * Day 12 问答来源返回对象。
 */
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
