package com.example.rag.model.enums;

/** 一次检索中的重排序执行状态。 */
public enum RerankStatus {
    DISABLED,
    SKIPPED_EMPTY,
    APPLIED,
    DEGRADED
}
