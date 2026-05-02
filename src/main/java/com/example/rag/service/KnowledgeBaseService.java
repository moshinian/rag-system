package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.request.CreateKnowledgeBaseRequest;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.KnowledgeBasePageQuery;
import com.example.rag.persistence.query.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 知识库管理服务。
 */
@Service
public class KnowledgeBaseService {

    private static final long DEFAULT_PAGE_NO = 1;
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 创建知识库。 */
    @Transactional
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
    public KnowledgeBaseResponse get(String kbCode) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        return toResponse(entity);
    }

    /** 禁用知识库。 */
    @Transactional
    public KnowledgeBaseResponse disable(String kbCode) {
        return updateStatus(kbCode, KnowledgeBaseStatus.INACTIVE);
    }

    /** 启用知识库。 */
    @Transactional
    public KnowledgeBaseResponse enable(String kbCode) {
        return updateStatus(kbCode, KnowledgeBaseStatus.ACTIVE);
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
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        entity.setStatus(status);
        knowledgeBaseRepository.updateById(entity);
        return toResponse(entity);
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
}
