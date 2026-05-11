package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 全量重嵌入异步提交结果。
 */
public record EmbeddingRebuildSubmitResponse(
        Long rebuildRunId,
        String status,
        String targetFingerprint,
        String embeddingModel,
        String embeddingProvider,
        Integer vectorDimensions,
        String distanceMetric,
        String operator,
        OffsetDateTime submittedAt
) {
}
