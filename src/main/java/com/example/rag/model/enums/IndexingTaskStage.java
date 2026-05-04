package com.example.rag.model.enums;

/**
 * 索引任务当前阶段。
 */
public enum IndexingTaskStage {
    QUEUED,
    DOCUMENT_PROCESSING,
    DOCUMENT_EMBEDDING,
    COMPLETED
}
