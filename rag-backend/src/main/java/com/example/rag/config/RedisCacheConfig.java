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

    /** 构造RedisCacheConfig。 */
    public RedisCacheConfig(RagCacheProperties ragCacheProperties) {
        this.ragCacheProperties = ragCacheProperties;
    }

    /** 配置基于 Redis 的缓存管理器。 */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisConnectionFactory connectionFactory = Objects.requireNonNull(redisConnectionFactory,
                "redisConnectionFactory must not be null");
        RedisSerializer<Object> valueSerializer = redisSerializer();
        // key 前缀和空值策略在默认配置层统一处理，避免每个 cache name 单独重复配置。
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

    /** 配置缓存异常处理器。 */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            /** 读取缓存失败时，优先清理坏缓存并允许回源。 */
            @Override
            public void handleCacheGetError(@NonNull RuntimeException exception,
                                            @NonNull org.springframework.cache.Cache cache,
                                            @NonNull Object key) {
                if (exception instanceof SerializationException) {
                    // 脏缓存说明旧值已不可读，直接驱逐并允许业务回源重建，比返回 500 更稳妥。
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

            /** 写入缓存失败时直接向上抛出异常。 */
            @Override
            public void handleCachePutError(@NonNull RuntimeException exception,
                                            @NonNull org.springframework.cache.Cache cache,
                                            @NonNull Object key,
                                            @Nullable Object value) {
                throw exception;
            }

            /** 删除缓存失败时直接向上抛出异常。 */
            @Override
            public void handleCacheEvictError(@NonNull RuntimeException exception,
                                              @NonNull org.springframework.cache.Cache cache,
                                              @NonNull Object key) {
                throw exception;
            }

            /** 清空缓存失败时直接向上抛出异常。 */
            @Override
            public void handleCacheClearError(@NonNull RuntimeException exception,
                                              @NonNull org.springframework.cache.Cache cache) {
                throw exception;
            }
        };
    }

    /** 为单个缓存配置应用带下限保护的 TTL。 */
    private RedisCacheConfiguration withTtl(RedisCacheConfiguration configuration, long ttlSeconds) {
        Duration ttl = Objects.requireNonNull(Duration.ofSeconds(Math.max(30, ttlSeconds)),
                "cache ttl must not be null");
        return configuration.entryTtl(ttl);
    }

    /** 构造带时间类型支持的 Redis 值序列化器。 */
    private RedisSerializer<Object> redisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer();

        serializer.configure(objectMapper -> {
            // 健康状态、时间戳等响应对象都直接走这个序列化器，因此需要统一启用 JavaTime 支持。
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        });

        return serializer;
    }
}
