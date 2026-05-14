package com.example.rag.model.dto;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.RetrievedChunkResponse;

import java.util.List;

/**
 * 问答历史中持久化的检索快照。
 */
public record RetrievedChunksSnapshot(
        RetrievalMode retrievalMode,
        String fusionStrategy,
        List<RetrievedChunkResponse> chunks
) {
}
