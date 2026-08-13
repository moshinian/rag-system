package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.CacheNames;
import com.example.rag.ingestion.storage.FileStorageService;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.KnowledgeBaseEnableResponse;
import com.example.rag.model.request.CreateKnowledgeBaseRequest;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.persistence.AgentActionRepository;
import com.example.rag.persistence.AgentRunEventRepository;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.AgentStepRepository;
import com.example.rag.persistence.ChatMessageRepository;
import com.example.rag.persistence.ChatSessionRepository;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.KnowledgeBasePageQuery;
import com.example.rag.persistence.query.PageResult;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 知识库管理服务。
 */
@Service
public class KnowledgeBaseService {
    private static final String TASK_TYPE_DOCUMENT_INDEXING = "DOCUMENT_INDEXING";
    private static final long DEFAULT_PAGE_NO = 1;
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IndexingTaskRepository indexingTaskRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentActionRepository agentActionRepository;
    private final AgentRunEventRepository agentRunEventRepository;
    private final FileStorageService fileStorageService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final DocumentIndexingService documentIndexingService;

    /** 构造KnowledgeBaseService。 */
    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository,
                                DocumentRepository documentRepository,
                                DocumentChunkRepository documentChunkRepository,
                                IndexingTaskRepository indexingTaskRepository,
                                ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                AgentRunRepository agentRunRepository,
                                AgentStepRepository agentStepRepository,
                                AgentActionRepository agentActionRepository,
                                AgentRunEventRepository agentRunEventRepository,
                                FileStorageService fileStorageService,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                DocumentIndexingService documentIndexingService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.indexingTaskRepository = indexingTaskRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.agentActionRepository = agentActionRepository;
        this.agentRunEventRepository = agentRunEventRepository;
        this.fileStorageService = fileStorageService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.documentIndexingService = documentIndexingService;
    }

    /** 创建知识库。 */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_PAGE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true)
    })
    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request) {
        // 同一 kbCode 不允许重复创建。
        knowledgeBaseRepository.findByCode(request.kbCode())
                .ifPresent(existing -> {
                    throw new BusinessException("Knowledge base already exists: " + request.kbCode());
                });

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setKbCode(request.kbCode().trim());
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setCreatedBy(defaultCreatedBy(request.createdBy()));
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);

        KnowledgeBaseEntity saved = knowledgeBaseRepository.insert(entity);
        return toResponse(saved);
    }

    /** 分页查询知识库。 */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.KNOWLEDGE_BASE_PAGE,
            key = "#status + ':' + (#pageNo == null ? 'null' : #pageNo) + ':' + (#pageSize == null ? 'null' : #pageSize)"
    )
    public PageResponse<KnowledgeBaseResponse> list(String status, Long pageNo, Long pageSize) {
        KnowledgeBaseStatus knowledgeBaseStatus = parseStatus(status);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        PageResult<KnowledgeBaseEntity> page = knowledgeBaseRepository.page(
                new KnowledgeBasePageQuery(knowledgeBaseStatus, normalizedPageNo, normalizedPageSize)
        );
        return new PageResponse<>(
                page.records().stream().map(this::toResponse).toList(),
                page.total(),
                page.pageNo(),
                page.pageSize()
        );
    }

    /** 查询知识库详情。 */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.KNOWLEDGE_BASE_DETAIL, key = "#kbCode")
    public KnowledgeBaseResponse get(String kbCode) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        return toResponse(entity);
    }

    /** 禁用知识库。 */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_DETAIL, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_PAGE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.QA_READINESS, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true)
    })
    public KnowledgeBaseResponse disable(String kbCode) {
        return updateStatus(kbCode, KnowledgeBaseStatus.INACTIVE);
    }

    /** 启用知识库。 */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_DETAIL, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_PAGE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.QA_READINESS, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true)
    })
    public KnowledgeBaseEnableResponse enable(String kbCode, boolean retryFailedIndexingTasks, String operator) {
        KnowledgeBaseEntity entity = updateStatusEntity(kbCode, KnowledgeBaseStatus.ACTIVE);
        // 恢复知识库和补偿失败任务是两个显式动作，默认只恢复可用性，不自动重放全部失败链路。
        DocumentIndexingService.BatchRetryIndexingResult retrySummary = retryFailedIndexingTasks
                ? documentIndexingService.retryLatestFailedTasksInKnowledgeBase(kbCode, operator)
                : new DocumentIndexingService.BatchRetryIndexingResult(0, 0, 0, 0, List.of());
        return toEnableResponse(entity, retryFailedIndexingTasks, retrySummary);
    }

    /** 物理删除知识库及其关联数据。 */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_DETAIL, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.KNOWLEDGE_BASE_PAGE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DOCUMENT_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DOCUMENT_PAGE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DOCUMENT_CHUNKS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.QA_READINESS, key = "#kbCode"),
            @CacheEvict(cacheNames = CacheNames.QA_RETRIEVAL, allEntries = true)
    })
    public KnowledgeBaseResponse delete(String kbCode) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        // 删除要求知识库内没有活跃索引任务，避免后台线程继续访问已被级联删掉的数据。
        if (indexingTaskRepository.existsActiveTaskInKnowledgeBase(entity.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
            throw new BusinessException("Knowledge base has active indexing tasks and cannot be deleted: " + kbCode);
        }
        if (agentRunRepository.existsByKnowledgeBaseIdAndStatus(entity.getId(), AgentRunStatus.RUNNING)
                || agentRunRepository.existsByKnowledgeBaseIdAndStatus(entity.getId(), AgentRunStatus.QUEUED)) {
            throw new BusinessException("Knowledge base has active agent runs and cannot be deleted: " + kbCode);
        }

        List<DocumentEntity> documents = documentRepository.findByKnowledgeBaseId(entity.getId());
        List<Long> sessionIds = chatSessionRepository.findIdsByKnowledgeBaseId(entity.getId());
        List<String> agentRunCodes = agentRunRepository.findRunCodesByKnowledgeBaseId(entity.getId());

        if (!sessionIds.isEmpty()) {
            chatMessageRepository.deleteBySessionIds(sessionIds);
        }
        // 这里按“消息 -> 会话 -> Agent轨迹 -> 任务/chunk/文档 -> 知识库”的顺序级联，尽量减少脏数据风险。
        chatSessionRepository.deleteByKnowledgeBaseId(entity.getId());
        if (!agentRunCodes.isEmpty()) {
            agentRunEventRepository.deleteByRunCodes(agentRunCodes);
            agentStepRepository.deleteByRunCodes(agentRunCodes);
            agentActionRepository.deleteByRunCodes(agentRunCodes);
            agentRunRepository.deleteByKnowledgeBaseId(entity.getId());
        }
        indexingTaskRepository.deleteByKnowledgeBaseId(entity.getId());
        documentChunkRepository.deleteByKnowledgeBaseId(entity.getId());
        documentRepository.deleteByKnowledgeBaseId(entity.getId());
        knowledgeBaseRepository.deleteById(entity.getId());

        deleteKnowledgeBaseMaterials(kbCode, documents);
        return toResponse(entity);
    }

    /** 把实体转换成返回对象。 */
    private KnowledgeBaseResponse toResponse(KnowledgeBaseEntity entity) {
        return new KnowledgeBaseResponse(
                entity.getId(),
                entity.getKbCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus().name(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** 把实体和恢复摘要转换成启用响应对象。 */
    private KnowledgeBaseEnableResponse toEnableResponse(KnowledgeBaseEntity entity,
                                                         boolean retryFailedIndexingTasks,
                                                         DocumentIndexingService.BatchRetryIndexingResult retrySummary) {
        return new KnowledgeBaseEnableResponse(
                entity.getId(),
                entity.getKbCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus().name(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                retryFailedIndexingTasks,
                retrySummary.retriedTaskCount(),
                retrySummary.skippedDisabledDocumentCount(),
                retrySummary.skippedActiveTaskDocumentCount(),
                retrySummary.skippedRetryLimitDocumentCount(),
                retrySummary.retriedDocumentCodes()
        );
    }

    /** 把空白字符串归一化成 null。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 没有传入创建人时，统一记为 system。 */
    private String defaultCreatedBy(String createdBy) {
        String normalized = trimToNull(createdBy);
        return normalized == null ? "system" : normalized;
    }

    /** 更新知识库状态。 */
    private KnowledgeBaseResponse updateStatus(String kbCode, KnowledgeBaseStatus status) {
        return toResponse(updateStatusEntity(kbCode, status));
    }

    /** 更新知识库状态并返回最新实体。 */
    private KnowledgeBaseEntity updateStatusEntity(String kbCode, KnowledgeBaseStatus status) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        if (status == KnowledgeBaseStatus.INACTIVE
                && indexingTaskRepository.existsActiveTaskInKnowledgeBase(entity.getId(), TASK_TYPE_DOCUMENT_INDEXING)) {
            // 禁用不是强停后台任务；当前实现要求先等索引链路自然结束，再切知识库状态。
            throw new BusinessException("Knowledge base has active indexing tasks and cannot be disabled: " + kbCode);
        }
        entity.setStatus(status);
        knowledgeBaseRepository.updateById(entity);
        return entity;
    }

    /** 解析知识库状态过滤条件。 */
    private KnowledgeBaseStatus parseStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        try {
            return KnowledgeBaseStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported knowledge base status: " + status);
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

    /** 删除知识库本地落盘物料。 */
    private void deleteKnowledgeBaseMaterials(String kbCode, List<DocumentEntity> documents) {
        boolean hasStoredFiles = documents.stream()
                .map(document -> document.getStoragePath())
                .anyMatch(path -> path != null && !path.isBlank());
        if (!hasStoredFiles) {
            return;
        }
        try {
            // 物料目录按知识库整体删除，避免逐文件清理与数据库级联范围不一致。
            fileStorageService.deleteKnowledgeBase(kbCode);
        } catch (IOException ex) {
            throw new BusinessException("Failed to delete knowledge base materials: " + ex.getMessage());
        }
    }
}
