package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
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
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("md", "txt", "pdf");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService localFileStorageService;

    public DocumentService(KnowledgeBaseRepository knowledgeBaseRepository,
                           DocumentRepository documentRepository,
                           LocalFileStorageService localFileStorageService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.localFileStorageService = localFileStorageService;
    }

    @Transactional
    public DocumentUploadResponse upload(String kbCode,
                                         MultipartFile file,
                                         String documentName,
                                         String tags,
                                         String source,
                                         String operator) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByKbCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        validateFile(file);

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = extractExtension(fileName);
        String contentHash = calculateSha256(file);

        if (documentRepository.existsByKnowledgeBaseIdAndContentHash(knowledgeBase.getId(), contentHash)) {
            throw new BusinessException("Duplicate document in knowledge base: " + kbCode);
        }

        String documentCode = generateDocumentCode();
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
        entity.setKnowledgeBase(knowledgeBase);
        entity.setDocumentCode(documentCode);
        entity.setFileName(fileName);
        entity.setDisplayName(displayName);
        entity.setFileType(fileType);
        entity.setStoragePath(storagePath);
        entity.setFileSize(file.getSize());
        entity.setContentHash(contentHash);
        entity.setStatus(DocumentStatus.UPLOADED);
        entity.setVersion(1);
        entity.setSource(trimToNull(source));
        entity.setTags(trimToNull(tags));
        entity.setCreatedBy(normalizedOperator);

        DocumentEntity saved = documentRepository.save(entity);
        return toResponse(saved, knowledgeBase.getKbCode());
    }

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

    private String sanitizeFileName(String originalFileName) {
        String normalized = originalFileName == null ? "" : originalFileName.trim();
        String fileName = normalized.replace("\\", "_").replace("/", "_");
        if (fileName.isBlank()) {
            throw new BusinessException("Uploaded file name must not be blank");
        }
        return fileName;
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            throw new BusinessException("File extension is required");
        }
        return fileName.substring(index + 1).toLowerCase();
    }

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

    private String generateDocumentCode() {
        return "DOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String normalizeDisplayName(String documentName, String fileName) {
        String normalized = trimToNull(documentName);
        return normalized == null ? fileName : normalized;
    }

    private String normalizeOperator(String operator) {
        String normalized = trimToNull(operator);
        return normalized == null ? "system" : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DocumentUploadResponse toResponse(DocumentEntity entity, String knowledgeBaseCode) {
        return new DocumentUploadResponse(
                entity.getId(),
                entity.getDocumentCode(),
                knowledgeBaseCode,
                entity.getFileName(),
                entity.getDisplayName(),
                entity.getFileType(),
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
