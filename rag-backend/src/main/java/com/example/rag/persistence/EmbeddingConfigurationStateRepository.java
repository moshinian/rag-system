package com.example.rag.persistence;

import com.example.rag.mapper.EmbeddingConfigurationStateMapper;
import com.example.rag.persistence.entity.EmbeddingConfigurationStateEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Embedding 配置状态访问层。
 */
@Repository
public class EmbeddingConfigurationStateRepository {

    private static final long SINGLETON_ID = 1L;

    private final EmbeddingConfigurationStateMapper mapper;

    public EmbeddingConfigurationStateRepository(EmbeddingConfigurationStateMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<EmbeddingConfigurationStateEntity> getSingleton() {
        return Optional.ofNullable(mapper.selectById(SINGLETON_ID));
    }

    public EmbeddingConfigurationStateEntity upsert(EmbeddingConfigurationStateEntity entity) {
        entity.setId(SINGLETON_ID);
        if (mapper.selectById(SINGLETON_ID) == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }
}
