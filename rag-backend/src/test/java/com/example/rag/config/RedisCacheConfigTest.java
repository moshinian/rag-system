package com.example.rag.config;

import com.example.rag.model.response.DocumentDetailResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheConfigTest {

    @Test
    void redisSerializerShouldRoundTripRecordResponses() throws Exception {
        RedisCacheConfig config = new RedisCacheConfig(new RagCacheProperties());
        Method method = RedisCacheConfig.class.getDeclaredMethod("redisSerializer");
        method.setAccessible(true);
        GenericJackson2JsonRedisSerializer serializer =
                (GenericJackson2JsonRedisSerializer) method.invoke(config);

        QuestionRetrievalResponse response = new QuestionRetrievalResponse(
                "kb-test",
                "问题",
                "bge-small-zh-v1.5",
                3,
                1,
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
        RedisCacheConfig config = new RedisCacheConfig(new RagCacheProperties());
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
}
