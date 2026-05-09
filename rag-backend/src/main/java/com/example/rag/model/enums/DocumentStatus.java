package com.example.rag.model.enums;

/**
 * 文档处理状态。
 */
public enum DocumentStatus {
    UPLOADED,
    PARSING,
    PARSED,
    CHUNKING,
    INDEXED,
    FAILED,
    DISABLED
}
