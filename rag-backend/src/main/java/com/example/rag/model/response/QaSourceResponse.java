package com.example.rag.model.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 问答来源返回对象。 */
public record QaSourceResponse(
        String documentCode,
        String documentName,
        @JsonSerialize(using = ToStringSerializer.class)
        Long chunkId,
        Integer chunkIndex,
        String content,
        Double score,
        Integer startOffset,
        Integer endOffset
) {
}
