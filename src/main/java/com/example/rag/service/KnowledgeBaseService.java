package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.entity.KnowledgeBaseEntity;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.request.CreateKnowledgeBaseRequest;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Transactional
    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request) {
        knowledgeBaseRepository.findByKbCode(request.kbCode())
                .ifPresent(existing -> {
                    throw new BusinessException("Knowledge base already exists: " + request.kbCode());
                });

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setKbCode(request.kbCode().trim());
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setCreatedBy(defaultCreatedBy(request.createdBy()));
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);

        KnowledgeBaseEntity saved = knowledgeBaseRepository.save(entity);
        return toResponse(saved);
    }

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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultCreatedBy(String createdBy) {
        String normalized = trimToNull(createdBy);
        return normalized == null ? "system" : normalized;
    }
}
