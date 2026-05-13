package com.example.rag.ingestion.storage;

import com.example.rag.config.RagStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储服务。
 */
@Service
public class LocalFileStorageService {
    private final RagStorageProperties ragStorageProperties;

    /** 构造LocalFileStorageService。 */
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

    /** 删除知识库对应的本地上传目录。 */
    public void deleteKnowledgeBaseDirectory(String kbCode) throws IOException {
        Path targetDirectory = baseDirectory().resolve(kbCode).normalize();
        if (!Files.exists(targetDirectory)) {
            return;
        }
        Files.walkFileTree(targetDirectory, new SimpleFileVisitor<>() {
            /** 删除目录时同步删除遍历到的文件。 */
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            /** 删除目录时在子项清理后删除当前目录。 */
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
