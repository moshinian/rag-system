package com.example.rag.ingestion.storage;

import com.example.rag.config.RagStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储服务。
 */
@Service
public class LocalFileStorageService {

    private final RagStorageProperties ragStorageProperties;

    public LocalFileStorageService(RagStorageProperties ragStorageProperties) {
        this.ragStorageProperties = ragStorageProperties;
    }

    /** 返回归一化后的存储根目录。 */
    public Path baseDirectory() {
        return Path.of(ragStorageProperties.getBaseDir()).toAbsolutePath().normalize();
    }

    /**
     * 把上传文件保存到本地目录。
     *
     * 目录结构按知识库和日期分层，文件名前缀使用 documentCode。
     */
    public Path store(String kbCode,
                      String datePath,
                      String documentCode,
                      String originalFileName,
                      MultipartFile file) throws IOException {
        Path targetDirectory = baseDirectory().resolve(kbCode).resolve(datePath).normalize();
        Files.createDirectories(targetDirectory);

        // 使用 documentCode 作为前缀，降低同名文件覆盖风险。
        Path targetFile = targetDirectory.resolve(documentCode + "_" + originalFileName).normalize();
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }
}
