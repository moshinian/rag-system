package com.example.rag.ingestion.storage;

import com.example.rag.config.RagStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "rag.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
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
    @Override
    public StoredFile store(String kbCode,
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
        String objectKey = kbCode + "/" + datePath + "/" + documentCode + "_" + originalFileName;
        return new StoredFile("local", objectKey, targetFile.toString());
    }

    @Override
    public MaterializedFile materialize(String objectKey, String legacyStoragePath) throws IOException {
        Path path;
        if (legacyStoragePath != null && !legacyStoragePath.isBlank()) {
            // 兼容迁移前已经落库的绝对路径；该字段只由服务端写入，不接受请求参数直接覆盖。
            path = Path.of(legacyStoragePath).toAbsolutePath().normalize();
        } else {
            path = baseDirectory().resolve(objectKey).normalize();
            if (!path.startsWith(baseDirectory())) {
                throw new IOException("Stored object key resolves outside configured base directory");
            }
        }
        if (!Files.exists(path)) {
            throw new IOException("Stored file not found: " + path);
        }
        return new MaterializedFile(path, false);
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

    @Override
    public void deleteKnowledgeBase(String kbCode) throws IOException {
        deleteKnowledgeBaseDirectory(kbCode);
    }
}
