package com.example.rag.support;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TestPaths {

    private TestPaths() {
    }

    public static Path backendRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("src"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Failed to locate rag-backend module root from current working directory");
    }

    public static Path backendFile(String relativePath) {
        return backendRoot().resolve(relativePath);
    }
}
