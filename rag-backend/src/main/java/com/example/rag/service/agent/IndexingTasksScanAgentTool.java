package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.enums.IndexingTaskStatus;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 只读索引任务扫描工具。
 */
@Component
public class IndexingTasksScanAgentTool implements AgentTool {
    public static final String TOOL_NAME = "indexing.tasks.scan";
    private static final int TASK_SCAN_LIMIT = 50;
    private static final int FAILED_TASK_SAMPLE_LIMIT = 5;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final ObjectMapper objectMapper;

    /** 构造IndexingTasksScanAgentTool。 */
    public IndexingTasksScanAgentTool(KnowledgeBaseRepository knowledgeBaseRepository,
                                      DocumentRepository documentRepository,
                                      IndexingTaskRepository indexingTaskRepository,
                                      ObjectMapper objectMapper) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.indexingTaskRepository = indexingTaskRepository;
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
        List<IndexingTaskEntity> tasks = indexingTaskRepository.findByKnowledgeBaseIdAndStatusesOrderByCreatedAtDesc(
                knowledgeBase.getId(),
                List.of(IndexingTaskStatus.QUEUED, IndexingTaskStatus.RUNNING, IndexingTaskStatus.SUCCEEDED, IndexingTaskStatus.FAILED),
                TASK_SCAN_LIMIT
        );
        Map<IndexingTaskStatus, Long> statusCounts = new EnumMap<>(IndexingTaskStatus.class);
        for (IndexingTaskStatus status : IndexingTaskStatus.values()) {
            statusCounts.put(status, 0L);
        }
        for (IndexingTaskEntity task : tasks) {
            IndexingTaskStatus status = task.getStatus();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
        }
        List<Map<String, Object>> failedTasks = tasks.stream()
                .filter(task -> task.getStatus() == IndexingTaskStatus.FAILED)
                .limit(FAILED_TASK_SAMPLE_LIMIT)
                .map(this::toFailedTask)
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("kbCode", context.kbCode());
        output.put("scannedTaskCount", tasks.size());
        output.put("statusCounts", statusCounts);
        output.put("failedTasks", failedTasks);
        return AgentToolResult.success(
                TOOL_NAME,
                AgentToolSupport.toJson(objectMapper, output),
                AgentToolSupport.elapsedMillis(startedAt)
        );
    }

    private Map<String, Object> toFailedTask(IndexingTaskEntity task) {
        Optional<DocumentEntity> document = documentRepository.findById(task.getDocumentId());
        Map<String, Object> failedTask = new LinkedHashMap<>();
        failedTask.put("taskId", task.getId());
        failedTask.put("documentId", task.getDocumentId());
        failedTask.put("documentCode", document.map(DocumentEntity::getDocumentCode).orElse(null));
        failedTask.put("taskType", task.getTaskType());
        failedTask.put("taskStage", task.getTaskStage());
        failedTask.put("retryCount", task.getRetryCount());
        failedTask.put("maxRetryCount", task.getMaxRetryCount());
        failedTask.put("errorMessage", task.getErrorMessage());
        return failedTask;
    }
}
