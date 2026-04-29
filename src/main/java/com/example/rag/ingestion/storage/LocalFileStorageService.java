package com.example.rag.ingestion.storage;

import com.example.rag.config.RagStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class LocalFileStorageService {

    private final RagStorageProperties ragStorageProperties;

    public LocalFileStorageService(RagStorageProperties ragStorageProperties) {
        this.ragStorageProperties = ragStorageProperties;
    }

    public Path baseDirectory() {
        return Path.of(ragStorageProperties.getBaseDir()).toAbsolutePath().normalize();
    }

    public Path store(String kbCode,
                      String datePath,
                      String documentCode,
                      String originalFileName,
                      MultipartFile file) throws IOException {
        Path targetDirectory = baseDirectory().resolve(kbCode).resolve(datePath).normalize();
        Files.createDirectories(targetDirectory);

        Path targetFile = targetDirectory.resolve(documentCode + "_" + originalFileName).normalize();
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }
}
