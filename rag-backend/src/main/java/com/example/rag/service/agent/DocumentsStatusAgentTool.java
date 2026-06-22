package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 只读文档状态扫描工具。
 */
@Component
public class DocumentsStatusAgentTool implements AgentTool {
    public static final String TOOL_NAME = "documents.status.scan";
    private static final int FAILED_DOCUMENT_SAMPLE_LIMIT = 5;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    /** 构造DocumentsStatusAgentTool。 */
    public DocumentsStatusAgentTool(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentRepository documentRepository,
                                    ObjectMapper objectMapper) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                AgentToolExecutionMode.READ_ONLY,
                AgentActionRiskLevel.LOW
        );
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        long startedAt = System.nanoTime();
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(context.kbCode())
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + context.kbCode()));
        List<DocumentEntity> documents = documentRepository.findByKnowledgeBaseId(knowledgeBase.getId());
        Map<DocumentStatus, Long> statusCounts = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : DocumentStatus.values()) {
            statusCounts.put(status, 0L);
        }
        for (DocumentEntity document : documents) {
            DocumentStatus status = document.getStatus();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
        }
        List<Map<String, Object>> failedDocuments = documents.stream()
                .filter(document -> document.getStatus() == DocumentStatus.FAILED)
                .limit(FAILED_DOCUMENT_SAMPLE_LIMIT)
                .map(this::toFailedDocument)
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("kbCode", context.kbCode());
        output.put("totalDocumentCount", documents.size());
        output.put("statusCounts", statusCounts);
        output.put("failedDocuments", failedDocuments);
        return AgentToolResult.success(
                TOOL_NAME,
                AgentToolSupport.toJson(objectMapper, output),
                AgentToolSupport.elapsedMillis(startedAt)
        );
    }

    private Map<String, Object> toFailedDocument(DocumentEntity document) {
        Map<String, Object> failedDocument = new LinkedHashMap<>();
        failedDocument.put("documentCode", document.getDocumentCode());
        failedDocument.put("documentName", displayName(document));
        failedDocument.put("status", document.getStatus());
        failedDocument.put("errorMessage", document.getErrorMessage());
        return failedDocument;
    }

    private String displayName(DocumentEntity document) {
        if (document.getDisplayName() != null && !document.getDisplayName().isBlank()) {
            return document.getDisplayName();
        }
        return document.getFileName();
    }
}
