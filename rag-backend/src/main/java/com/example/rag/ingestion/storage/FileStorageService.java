package com.example.rag.ingestion.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 原始文档存储抽象，业务层不感知 Local 或 MinIO。 */
public interface FileStorageService {
    StoredFile store(String kbCode,
                     String datePath,
                     String documentCode,
                     String originalFileName,
                     MultipartFile file) throws IOException;

    MaterializedFile materialize(String objectKey, String legacyStoragePath) throws IOException;

    void deleteKnowledgeBase(String kbCode) throws IOException;
}
