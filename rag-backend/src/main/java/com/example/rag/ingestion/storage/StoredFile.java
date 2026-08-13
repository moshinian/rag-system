package com.example.rag.ingestion.storage;

/** 文件成功写入存储后的稳定引用。 */
public record StoredFile(String storageType, String objectKey, String storagePath) {
}
