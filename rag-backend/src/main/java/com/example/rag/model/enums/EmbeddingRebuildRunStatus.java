package com.example.rag.model.enums;

/**
 * 全量重嵌入任务状态。
 */
public enum EmbeddingRebuildRunStatus {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    SUCCEEDED,
    FAILED
}
