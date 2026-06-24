package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 只读文档状态扫描工具。
 */
@Component
public class DocumentsStatusAgentTool implements McpTool {
    public static final String TOOL_NAME = "documents.status.scan";
    private static final int FAILED_DOCUMENT_SAMPLE_LIMIT = 5;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;

    /** 注入知识库和文档查询能力。 */
    public DocumentsStatusAgentTool(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentRepository documentRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
    }

    /** 返回 MCP 工具唯一名称。 */
    @Override
    public String name() {
        return TOOL_NAME;
    }

    /** 返回工具展示标题。 */
    @Override
    public String title() {
        return TOOL_NAME;
    }

    /** 返回供 planner 理解工具用途的描述。 */
    @Override
    public String description() {
        return "扫描指定知识库的文档状态分布，并返回少量失败文档摘要。";
    }

    /** 声明仅接收必填 kbCode 的严格输入 schema。 */
    @Override
    public Map<String, Object> inputSchema() {
        return AgentToolSupport.objectSchema(
                List.of("kbCode"),
                Map.of("kbCode", AgentToolSupport.stringProperty("知识库编码。"))
        );
    }

    /** 汇总文档状态分布，并裁剪少量失败文档供诊断使用。 */
    @Override
    public McpToolResult call(McpToolContext context) {
        long startedAt = System.nanoTime();
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(context.kbCode())
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + context.kbCode()));
        List<DocumentEntity> documents = documentRepository.findByKnowledgeBaseId(knowledgeBase.getId());
        Map<DocumentStatus, Long> statusCounts = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : DocumentStatus.values()) {
            statusCounts.put(status, 0L);
        }
        // 先预置全部枚举值，保证没有文档的状态也稳定返回 0。
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
        return McpToolResult.success(
                TOOL_NAME,
                output,
                AgentToolSupport.elapsedMillis(startedAt)
        );
    }

    /** 把失败文档裁剪为 Agent 诊断需要的最小字段集合。 */
    private Map<String, Object> toFailedDocument(DocumentEntity document) {
        Map<String, Object> failedDocument = new LinkedHashMap<>();
        failedDocument.put("documentCode", document.getDocumentCode());
        failedDocument.put("documentName", displayName(document));
        failedDocument.put("status", document.getStatus());
        failedDocument.put("errorMessage", document.getErrorMessage());
        return failedDocument;
    }

    /** 优先返回用户可读名称，缺失时退回原始文件名。 */
    private String displayName(DocumentEntity document) {
        if (document.getDisplayName() != null && !document.getDisplayName().isBlank()) {
            return document.getDisplayName();
        }
        return document.getFileName();
    }
}
