package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.ingestion.storage.LocalFileStorageService;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.response.DocumentChunkResponse;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentDetailResponse;
import com.example.rag.model.response.DocumentSummaryResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.DocumentPageQuery;
import com.example.rag.persistence.query.PageResult;
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
import java.util.List;
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
    private static final long DEFAULT_PAGE_NO = 1;
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;
    private static final Map<String, String> DEFAULT_MEDIA_TYPES = Map.of(
            "md", "text/markdown",
            "txt", "text/plain",
            "pdf", "application/pdf"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService localFileStorageService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public DocumentService(KnowledgeBaseRepository knowledgeBaseRepository,
                           DocumentChunkRepository documentChunkRepository,
                           DocumentRepository documentRepository,
                           LocalFileStorageService localFileStorageService,
                           SnowflakeIdGenerator snowflakeIdGenerator) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
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
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        ensureKnowledgeBaseActive(knowledgeBase);

        validateFile(file);

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = extractExtension(fileName);
        String mediaType = resolveMediaType(file, fileType);
        String contentHash = calculateSha256(file);

        // 当前阶段以“同知识库内容去重”为准，避免重复 chunk 污染后续检索结果。
        if (documentRepository.existsInKnowledgeBaseByContentHash(knowledgeBase.getId(), contentHash)) {
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
        entity.setKnowledgeBaseId(knowledgeBase.getId());
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
        DocumentEntity saved = documentRepository.insert(entity);
        return toResponse(saved, knowledgeBase.getKbCode());
    }

    /** 分页查询知识库下的文档。 */
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> listDocuments(String kbCode,
                                                               String status,
                                                               Long pageNo,
                                                               Long pageSize) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        DocumentStatus documentStatus = parseStatus(status);
        DocumentPageQuery query = new DocumentPageQuery(
                knowledgeBase.getId(),
                documentStatus,
                normalizedPageNo,
                normalizedPageSize
        );
        PageResult<DocumentEntity> page = documentRepository.pageByKnowledgeBase(query);
        List<DocumentSummaryResponse> records = page.records().stream()
                .map(document -> toSummaryResponse(document, knowledgeBase.getKbCode()))
                .toList();
        return new PageResponse<>(records, page.total(), page.pageNo(), page.pageSize());
    }

    /** 查询文档详情。 */
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocument(String kbCode, String documentCode) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        return toDetailResponse(document, knowledgeBase.getKbCode());
    }

    /** 禁用文档。 */
    @Transactional
    public DocumentDetailResponse disableDocument(String kbCode, String documentCode) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        document.setStatus(DocumentStatus.DISABLED);
        documentRepository.updateById(document);
        return toDetailResponse(document, knowledgeBase.getKbCode());
    }

    /** 查询文档的全部 chunk。 */
    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> listDocumentChunks(String kbCode, String documentCode) {
        knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        DocumentEntity document = documentRepository.findByCodeInKnowledgeBase(documentCode, kbCode)
                .orElseThrow(() -> new BusinessException("Document not found in knowledge base: " + documentCode));
        return documentChunkRepository.findByDocumentIdOrderByChunkIndex(document.getId()).stream()
                .map(this::toChunkResponse)
                .toList();
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

    /** 校验知识库是否仍处于启用状态。 */
    private void ensureKnowledgeBaseActive(KnowledgeBaseEntity knowledgeBase) {
        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BusinessException("Knowledge base is inactive: " + knowledgeBase.getKbCode());
        }
    }

    /** 解析文档状态过滤条件。 */
    private DocumentStatus parseStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported document status: " + status);
        }
    }

    /** 归一化页码。 */
    private long normalizePageNo(Long pageNo) {
        if (pageNo == null) {
            return DEFAULT_PAGE_NO;
        }
        if (pageNo < 1) {
            throw new BusinessException("Page number must be greater than 0");
        }
        return pageNo;
    }

    /** 归一化分页大小。 */
    private long normalizePageSize(Long pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return pageSize;
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

    /** 把持久化对象转换成列表项响应。 */
    private DocumentSummaryResponse toSummaryResponse(DocumentEntity entity, String knowledgeBaseCode) {
        return new DocumentSummaryResponse(
                entity.getId(),
                entity.getDocumentCode(),
                knowledgeBaseCode,
                entity.getFileName(),
                entity.getDisplayName(),
                entity.getFileType(),
                entity.getMediaType(),
                entity.getFileSize(),
                entity.getStatus().name(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** 把持久化对象转换成详情响应。 */
    private DocumentDetailResponse toDetailResponse(DocumentEntity entity, String knowledgeBaseCode) {
        return new DocumentDetailResponse(
                entity.getId(),
                entity.getDocumentCode(),
                knowledgeBaseCode,
                entity.getFileName(),
                entity.getDisplayName(),
                entity.getFileType(),
                entity.getMediaType(),
                entity.getStoragePath(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getStatus().name(),
                entity.getVersion(),
                entity.getSource(),
                entity.getTags(),
                entity.getErrorMessage(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** 把持久化对象转换成 chunk 响应。 */
    private DocumentChunkResponse toChunkResponse(DocumentChunkEntity entity) {
        return new DocumentChunkResponse(
                entity.getId(),
                entity.getDocumentId(),
                entity.getChunkIndex(),
                entity.getChunkType(),
                entity.getTitle(),
                entity.getContent(),
                entity.getContentLength(),
                entity.getTokenCount(),
                entity.getStartOffset(),
                entity.getEndOffset(),
                entity.getMetadataJson(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
