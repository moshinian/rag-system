package com.example.rag.config;

import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Redis 中已有坏缓存时，Spring 缓存切面会使用自定义错误处理器自愈。
 */
@SpringBootTest(
        classes = RedisCacheConfigIntegrationTest.TestApplication.class,
        properties = {
                "spring.cache.type=redis",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "spring.data.redis.password=rag_password",
                "spring.data.redis.timeout=3s",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
class RedisCacheConfigIntegrationTest {

    private static final String KB_CODE = "itest-kb";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TestCachedService testCachedService;

    @BeforeEach
    @AfterEach
    void cleanRedisKey() {
        stringRedisTemplate.delete(redisKey(KB_CODE));
        testCachedService.resetInvocationCount();
    }

    @Test
    void cacheableMethodShouldEvictUnreadableRedisEntryAndRebuildCache() {
        stringRedisTemplate.opsForValue().set(redisKey(KB_CODE), "not-json-at-all");

        QuestionAnsweringReadinessResponse first = testCachedService.getReadiness(KB_CODE);

        assertThat(first.knowledgeBaseCode()).isEqualTo(KB_CODE);
        assertThat(testCachedService.invocationCount()).isEqualTo(1);
        assertThat(stringRedisTemplate.opsForValue().get(redisKey(KB_CODE)))
                .contains("\"@class\":\"com.example.rag.model.response.QuestionAnsweringReadinessResponse\"");

        QuestionAnsweringReadinessResponse second = testCachedService.getReadiness(KB_CODE);

        assertThat(second).isEqualTo(first);
        assertThat(testCachedService.invocationCount()).isEqualTo(1);
    }

    private static @NonNull String redisKey(@NonNull String kbCode) {
        return "rag:" + CacheNames.QA_READINESS + "::" + kbCode;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(RagCacheProperties.class)
    @Import({RedisCacheConfig.class, TestCachedService.class})
    static class TestApplication {
    }

    static class TestCachedService {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Cacheable(cacheNames = CacheNames.QA_READINESS, key = "#kbCode")
        public QuestionAnsweringReadinessResponse getReadiness(@NonNull String kbCode) {
            invocationCount.incrementAndGet();
            return new QuestionAnsweringReadinessResponse(
                    kbCode,
                    "ACTIVE",
                    true,
                    "itest-provider",
                    "itest-model",
                    "itest-model",
                    1024,
                    "pgvector",
                    5,
                    10L,
                    10L,
                    false,
                    false,
                    null,
                    "Ready"
            );
        }

        int invocationCount() {
            return invocationCount.get();
        }

        void resetInvocationCount() {
            invocationCount.set(0);
        }
    }
}
