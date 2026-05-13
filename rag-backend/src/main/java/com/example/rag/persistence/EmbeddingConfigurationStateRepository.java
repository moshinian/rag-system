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

    /** 注入 embedding 配置状态 Mapper。 */
    public EmbeddingConfigurationStateRepository(EmbeddingConfigurationStateMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取唯一一条 embedding 配置状态记录。 */
    public Optional<EmbeddingConfigurationStateEntity> getSingleton() {
        return Optional.ofNullable(mapper.selectById(SINGLETON_ID));
    }

    /** 按固定主键写入或更新 embedding 配置状态。 */
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
