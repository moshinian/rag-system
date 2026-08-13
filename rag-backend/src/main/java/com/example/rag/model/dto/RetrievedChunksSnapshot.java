package com.example.rag.model.dto;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.enums.RerankStatus;
import com.example.rag.model.response.RetrievedChunkResponse;

import java.util.List;

/**
 * 问答历史中持久化的检索快照。
 */
public record RetrievedChunksSnapshot(
        RetrievalMode retrievalMode,
        String fusionStrategy,
        RerankStatus rerankStatus,
        String rerankModel,
        int rerankCandidateCount,
        long rerankDurationMs,
        List<RetrievedChunkResponse> chunks
) {
    /** 兼容未接入重排序前的历史快照构造调用。 */
    public RetrievedChunksSnapshot(RetrievalMode retrievalMode,
                                   String fusionStrategy,
                                   List<RetrievedChunkResponse> chunks) {
        this(retrievalMode, fusionStrategy, RerankStatus.DISABLED, null, 0, 0L, chunks);
    }
}
