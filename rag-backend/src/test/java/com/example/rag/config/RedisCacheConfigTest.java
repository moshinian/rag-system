package com.example.rag.config;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.DocumentDetailResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheConfigTest {

    @Mock
    private Cache cache;

    private RedisCacheConfig config;

    @BeforeEach
    void setUp() {
        config = new RedisCacheConfig(new RagCacheProperties());
    }

    @Test
    void redisSerializerShouldRoundTripRecordResponses() throws Exception {
        Method method = RedisCacheConfig.class.getDeclaredMethod("redisSerializer");
        method.setAccessible(true);
        GenericJackson2JsonRedisSerializer serializer =
                (GenericJackson2JsonRedisSerializer) method.invoke(config);

        QuestionRetrievalResponse response = new QuestionRetrievalResponse(
                "kb-test",
                "问题",
                "bge-small-zh-v1.5",
                3,
                RetrievalMode.DENSE,
                "NONE",
                1,
                0,
                1,
                11L,
                0L,
                0L,
                11L,
                List.of(new RetrievedChunkResponse(
                        1L,
                        2L,
                        "DOC-1",
                        "测试文档",
                        0,
                        "TEXT",
                        "测试内容",
                        0,
                        4,
                        "bge-small-zh-v1.5",
                        0.9D
                ))
        );

        byte[] bytes = serializer.serialize(response);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(QuestionRetrievalResponse.class);
        assertThat(restored).isEqualTo(response);
    }

    @Test
    void redisSerializerShouldRoundTripResponsesWithOffsetDateTime() throws Exception {
        Method method = RedisCacheConfig.class.getDeclaredMethod("redisSerializer");
        method.setAccessible(true);
        GenericJackson2JsonRedisSerializer serializer =
                (GenericJackson2JsonRedisSerializer) method.invoke(config);

        DocumentDetailResponse response = new DocumentDetailResponse(
                1L,
                "DOC-1",
                "kb-test",
                "sample.md",
                "测试文档",
                "md",
                "text/markdown",
                "/tmp/sample.md",
                123L,
                "hash",
                "INDEXED",
                null,
                1,
                "source",
                "tag",
                null,
                "codex",
                OffsetDateTime.parse("2026-05-05T10:00:00+08:00"),
                OffsetDateTime.parse("2026-05-05T10:01:00+08:00")
        );

        byte[] bytes = serializer.serialize(response);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(DocumentDetailResponse.class);
        DocumentDetailResponse restoredResponse =
                (DocumentDetailResponse) Objects.requireNonNull(restored, "restored response must not be null");
        assertThat(restoredResponse.id()).isEqualTo(response.id());
        assertThat(restoredResponse.documentCode()).isEqualTo(response.documentCode());
        assertThat(restoredResponse.knowledgeBaseCode()).isEqualTo(response.knowledgeBaseCode());
        assertThat(restoredResponse.createdAt().toInstant()).isEqualTo(response.createdAt().toInstant());
        assertThat(restoredResponse.updatedAt().toInstant()).isEqualTo(response.updatedAt().toInstant());
    }

    @Test
    void cacheErrorHandlerShouldEvictUnreadableEntryAndSuppressSerializationException() {
        when(cache.getName()).thenReturn("documentChunks");
        CacheErrorHandler handler = config.errorHandler();

        assertThatCode(() -> handler.handleCacheGetError(
                new SerializationException("bad cache payload"),
                Objects.requireNonNull(cache, "cache mock must not be null"),
                "settlement-kb:DOC-1"))
                .doesNotThrowAnyException();

        verify(cache).evictIfPresent("settlement-kb:DOC-1");
    }

    @Test
    void cacheErrorHandlerShouldRethrowNonSerializationException() {
        CacheErrorHandler handler = config.errorHandler();

        assertThatThrownBy(() -> handler.handleCacheGetError(
                new IllegalStateException("boom"),
                Objects.requireNonNull(cache, "cache mock must not be null"),
                "settlement-kb:DOC-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
