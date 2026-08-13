package com.example.rag.ingestion.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 将对象存储内容临时物化为解析器可读取的本地 Path。 */
public record MaterializedFile(Path path, boolean temporary) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        if (temporary) {
            Files.deleteIfExists(path);
        }
    }
}
