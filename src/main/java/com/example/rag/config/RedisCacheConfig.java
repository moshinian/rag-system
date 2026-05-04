package com.example.rag.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置。
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    private final RagCacheProperties ragCacheProperties;

    public RedisCacheConfig(RagCacheProperties ragCacheProperties) {
        this.ragCacheProperties = ragCacheProperties;
    }

    /** 配置基于 Redis 的缓存管理器。 */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer()))
                .disableCachingNullValues()
                .prefixCacheNameWith("rag:");

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(CacheNames.KNOWLEDGE_BASE_DETAIL, withTtl(defaultConfiguration,
                ragCacheProperties.getKnowledgeBaseDetailTtlSeconds()));
        cacheConfigurations.put(CacheNames.KNOWLEDGE_BASE_PAGE, withTtl(defaultConfiguration,
                ragCacheProperties.getKnowledgeBasePageTtlSeconds()));
        cacheConfigurations.put(CacheNames.DOCUMENT_DETAIL, withTtl(defaultConfiguration,
                ragCacheProperties.getDocumentDetailTtlSeconds()));
        cacheConfigurations.put(CacheNames.DOCUMENT_PAGE, withTtl(defaultConfiguration,
                ragCacheProperties.getDocumentPageTtlSeconds()));
        cacheConfigurations.put(CacheNames.DOCUMENT_CHUNKS, withTtl(defaultConfiguration,
                ragCacheProperties.getDocumentChunksTtlSeconds()));
        cacheConfigurations.put(CacheNames.QA_READINESS, withTtl(defaultConfiguration,
                ragCacheProperties.getQaReadinessTtlSeconds()));
        cacheConfigurations.put(CacheNames.QA_RETRIEVAL, withTtl(defaultConfiguration,
                ragCacheProperties.getQaRetrievalTtlSeconds()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration withTtl(RedisCacheConfiguration configuration, long ttlSeconds) {
        return configuration.entryTtl(Duration.ofSeconds(Math.max(30, ttlSeconds)));
    }

    private GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
