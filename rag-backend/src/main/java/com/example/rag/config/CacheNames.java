package com.example.rag.config;

/**
 * Redis 缓存名称常量。
 */
public final class CacheNames {

    public static final String KNOWLEDGE_BASE_DETAIL = "knowledgeBaseDetail";
    public static final String KNOWLEDGE_BASE_PAGE = "knowledgeBasePage";
    public static final String DOCUMENT_DETAIL = "documentDetail";
    public static final String DOCUMENT_PAGE = "documentPage";
    public static final String DOCUMENT_CHUNKS = "documentChunks";
    public static final String QA_READINESS = "qaReadiness";
    public static final String QA_RETRIEVAL = "qaRetrieval";

    private CacheNames() {
    }
}
