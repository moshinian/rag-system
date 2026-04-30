package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.entity.KnowledgeBaseEntity;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.request.CreateKnowledgeBaseRequest;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库管理服务。
 */
@Service
public class KnowledgeBaseService {

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
        knowledgeBaseRepository.findByKbCode(request.kbCode())
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

        KnowledgeBaseEntity saved = knowledgeBaseRepository.save(entity);
        return toResponse(saved);
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
}
