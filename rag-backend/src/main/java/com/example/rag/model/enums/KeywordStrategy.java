package com.example.rag.model.enums;

/**
 * Hybrid 检索中 keyword/lexical 分支的实现策略。
 */
public enum KeywordStrategy {
    LIKE,
    POSTGRES_FTS
}
