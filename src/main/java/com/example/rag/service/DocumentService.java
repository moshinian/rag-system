package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.ingestion.storage.LocalFileStorageService;
import com.example.rag.model.entity.DocumentEntity;
import com.example.rag.model.entity.KnowledgeBaseEntity;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文档上传服务。
 *
 * 负责校验上传文件、计算内容摘要、保存原始文件，并将文档元数据写入数据库。
 * 该服务不负责解析和切块，避免把上传接口扩展为同步长链路。
 */
@Service
public class DocumentService {

    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("md", "txt", "pdf");
    private static final Map<String, String> DEFAULT_MEDIA_TYPES = Map.of(
            "md", "text/markdown",
            "txt", "text/plain",
            "pdf", "application/pdf"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService localFileStorageService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public DocumentService(KnowledgeBaseRepository knowledgeBaseRepository,
                           DocumentRepository documentRepository,
                           LocalFileStorageService localFileStorageService,
                           SnowflakeIdGenerator snowflakeIdGenerator) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.localFileStorageService = localFileStorageService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 上传原始文档并保存元数据。 */
    @Transactional
    public DocumentUploadResponse upload(String kbCode,
                                         MultipartFile file,
                                         String documentName,
                                         String tags,
                                         String source,
                                         String operator) {
        // 先确认知识库存在，再进入文件上传流程。
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByKbCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        validateFile(file);

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = extractExtension(fileName);
        String mediaType = resolveMediaType(file, fileType);
        String contentHash = calculateSha256(file);

        // 当前阶段以“同知识库内容去重”为准，避免重复 chunk 污染后续检索结果。
        if (documentRepository.existsByKnowledgeBaseIdAndContentHash(knowledgeBase.getId(), contentHash)) {
            throw new BusinessException("Duplicate document in knowledge base: " + kbCode);
        }

        long documentId = snowflakeIdGenerator.nextId();
        String documentCode = generateDocumentCode(documentId);
        String displayName = normalizeDisplayName(documentName, fileName);
        String normalizedOperator = normalizeOperator(operator);
        String storagePath;
        try {
            storagePath = localFileStorageService.store(
                    knowledgeBase.getKbCode(),
                    LocalDate.now().format(DATE_FORMATTER),
                    documentCode,
                    fileName,
                    file
            ).toString();
        } catch (IOException ex) {
            throw new BusinessException("Failed to store document: " + ex.getMessage());
        }

        DocumentEntity entity = new DocumentEntity();
        entity.setId(documentId);
        entity.setKnowledgeBase(knowledgeBase);
        entity.setDocumentCode(documentCode);
        entity.setFileName(fileName);
        entity.setDisplayName(displayName);
        entity.setFileType(fileType);
        entity.setMediaType(mediaType);
        entity.setStoragePath(storagePath);
        entity.setFileSize(file.getSize());
        entity.setContentHash(contentHash);
        entity.setStatus(DocumentStatus.UPLOADED);
        entity.setVersion(1);
        entity.setSource(trimToNull(source));
        entity.setTags(trimToNull(tags));
        entity.setCreatedBy(normalizedOperator);

        // 上传阶段只写入初始状态，后续处理由独立链路继续推进。
        DocumentEntity saved = documentRepository.save(entity);
        return toResponse(saved, knowledgeBase.getKbCode());
    }

    /** 执行必要的上传校验。 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded file must not be empty");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException("Uploaded file name must not be blank");
        }

        String sanitizedFileName = sanitizeFileName(originalFileName);
        String fileType = extractExtension(sanitizedFileName);
        if (!SUPPORTED_FILE_TYPES.contains(fileType)) {
            throw new BusinessException("Unsupported file type: " + fileType);
        }
    }

    /** 清理文件名，避免异常路径字符进入存储路径。 */
    private String sanitizeFileName(String originalFileName) {
        String normalized = originalFileName == null ? "" : originalFileName.trim();
        String fileName = normalized.replace("\\", "_").replace("/", "_");
        if (fileName.isBlank()) {
            throw new BusinessException("Uploaded file name must not be blank");
        }
        return fileName;
    }

    /** 从文件名中提取扩展名。 */
    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            throw new BusinessException("File extension is required");
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    /** 基于文件内容计算 SHA-256。 */
    private String calculateSha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException("Failed to calculate file hash: " + ex.getMessage());
        }
    }

    /**
     * 解析媒体类型。
     *
     * 优先使用 multipart 自带的 content-type；
     * 如果客户端没有传，则退回到基于扩展名的默认媒体类型。
     */
    private String resolveMediaType(MultipartFile file, String fileType) {
        String contentType = trimToNull(file.getContentType());
        if (contentType != null) {
            int parameterIndex = contentType.indexOf(';');
            String normalized = parameterIndex >= 0
                    ? contentType.substring(0, parameterIndex)
                    : contentType;
            String mediaType = normalized.trim().toLowerCase(Locale.ROOT);
            if (!mediaType.isEmpty()) {
                return mediaType;
            }
        }
        return DEFAULT_MEDIA_TYPES.getOrDefault(fileType, "application/octet-stream");
    }

    /** 基于雪花 ID 生成文档编码。 */
    private String generateDocumentCode(long documentId) {
        return "DOC-" + documentId;
    }

    /** 没有显式传入文档名时，退回到原始文件名。 */
    private String normalizeDisplayName(String documentName, String fileName) {
        String normalized = trimToNull(documentName);
        return normalized == null ? fileName : normalized;
    }

    /** 没有传入操作人时，统一记为 system。 */
    private String normalizeOperator(String operator) {
        String normalized = trimToNull(operator);
        return normalized == null ? "system" : normalized;
    }

    /** 把空白字符串归一化成 null。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 把实体转换成接口返回对象。 */
    private DocumentUploadResponse toResponse(DocumentEntity entity, String knowledgeBaseCode) {
        return new DocumentUploadResponse(
                entity.getId(),
                entity.getDocumentCode(),
                knowledgeBaseCode,
                entity.getFileName(),
                entity.getDisplayName(),
                entity.getFileType(),
                entity.getMediaType(),
                entity.getFileSize(),
                entity.getStoragePath(),
                entity.getContentHash(),
                entity.getStatus().name(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
