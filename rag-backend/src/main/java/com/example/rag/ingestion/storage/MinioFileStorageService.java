package com.example.rag.ingestion.storage;

import com.example.rag.config.RagStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Kubernetes 环境使用的 MinIO 共享对象存储实现。 */
@Service
@ConditionalOnProperty(prefix = "rag.storage", name = "type", havingValue = "minio")
public class MinioFileStorageService implements FileStorageService {
    private final MinioClient client;
    private final String bucket;

    public MinioFileStorageService(RagStorageProperties properties) {
        RagStorageProperties.Minio minio = properties.getMinio();
        this.client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        this.bucket = minio.getBucket();
    }

    @Override
    public StoredFile store(String kbCode,
                            String datePath,
                            String documentCode,
                            String originalFileName,
                            MultipartFile file) throws IOException {
        String objectKey = kbCode + "/" + datePath + "/" + documentCode + "_" + originalFileName;
        try {
            ensureBucket();
            try (InputStream input = file.getInputStream()) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(input, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            return new StoredFile("minio", objectKey, "minio://" + bucket + "/" + objectKey);
        } catch (Exception ex) {
            throw asIOException("Failed to store MinIO object " + objectKey, ex);
        }
    }

    @Override
    public MaterializedFile materialize(String objectKey, String legacyStoragePath) throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IOException("MinIO object key is missing");
        }
        String suffix = objectKey.contains(".") ? objectKey.substring(objectKey.lastIndexOf('.')) : ".bin";
        Path temporary = Files.createTempFile("rag-object-", suffix);
        try (InputStream input = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new MaterializedFile(temporary, true);
        } catch (Exception ex) {
            Files.deleteIfExists(temporary);
            throw asIOException("Failed to load MinIO object " + objectKey, ex);
        }
    }

    @Override
    public void deleteKnowledgeBase(String kbCode) throws IOException {
        List<DeleteObject> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(kbCode + "/")
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                objects.add(new DeleteObject(result.get().objectName()));
            }
            if (!objects.isEmpty()) {
                client.removeObjects(RemoveObjectsArgs.builder().bucket(bucket).objects(objects).build())
                        .forEach(result -> {
                            try {
                                result.get();
                            } catch (Exception ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
            }
        } catch (Exception ex) {
            throw asIOException("Failed to delete MinIO prefix " + kbCode, ex);
        }
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private IOException asIOException(String message, Exception ex) {
        return ex instanceof IOException io ? io : new IOException(message + ": " + ex.getMessage(), ex);
    }
}
