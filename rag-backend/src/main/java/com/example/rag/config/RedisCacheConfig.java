package com.example.rag.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Redis 缓存配置。
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    private final RagCacheProperties ragCacheProperties;

    public RedisCacheConfig(RagCacheProperties ragCacheProperties) {
        this.ragCacheProperties = ragCacheProperties;
    }

    /** 配置基于 Redis 的缓存管理器。 */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisConnectionFactory connectionFactory = Objects.requireNonNull(redisConnectionFactory,
                "redisConnectionFactory must not be null");
        RedisSerializer<Object> valueSerializer = redisSerializer();
        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        Objects.requireNonNull(valueSerializer, "redis value serializer must not be null")))
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

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(@NonNull RuntimeException exception,
                                            @NonNull org.springframework.cache.Cache cache,
                                            @NonNull Object key) {
                if (exception instanceof SerializationException) {
                    log.warn("Ignoring unreadable Redis cache entry. cache={}, key={}, reason={}",
                            cache.getName(), key, exception.getMessage());
                    try {
                        cache.evictIfPresent(key);
                    } catch (RuntimeException evictionException) {
                        log.warn("Failed to evict unreadable Redis cache entry. cache={}, key={}, reason={}",
                                cache.getName(), key, evictionException.getMessage());
                    }
                    return;
                }
                throw exception;
            }

            @Override
            public void handleCachePutError(@NonNull RuntimeException exception,
                                            @NonNull org.springframework.cache.Cache cache,
                                            @NonNull Object key,
                                            @Nullable Object value) {
                throw exception;
            }

            @Override
            public void handleCacheEvictError(@NonNull RuntimeException exception,
                                              @NonNull org.springframework.cache.Cache cache,
                                              @NonNull Object key) {
                throw exception;
            }

            @Override
            public void handleCacheClearError(@NonNull RuntimeException exception,
                                              @NonNull org.springframework.cache.Cache cache) {
                throw exception;
            }
        };
    }

    private RedisCacheConfiguration withTtl(RedisCacheConfiguration configuration, long ttlSeconds) {
        Duration ttl = Objects.requireNonNull(Duration.ofSeconds(Math.max(30, ttlSeconds)),
                "cache ttl must not be null");
        return configuration.entryTtl(ttl);
    }

    private RedisSerializer<Object> redisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer();

        serializer.configure(objectMapper -> {
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        });

        return serializer;
    }
}
