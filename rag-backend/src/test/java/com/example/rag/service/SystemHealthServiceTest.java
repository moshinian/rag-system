package com.example.rag.service;

import com.example.rag.config.RagAiGatewayProperties;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagLlmProperties;
import com.example.rag.integration.ai.AiGatewayClient;
import com.example.rag.model.response.HealthStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemHealthServiceTest {

    private Environment environment;
    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private AiGatewayClient aiGatewayClient;
    private RagEmbeddingProperties ragEmbeddingProperties;
    private RagLlmProperties ragLlmProperties;
    private RagAiGatewayProperties ragAiGatewayProperties;
    private SystemHealthService systemHealthService;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        stringRedisTemplate = new FixedPingStringRedisTemplate();
        aiGatewayClient = mock(AiGatewayClient.class);
        ragEmbeddingProperties = new RagEmbeddingProperties();
        ragLlmProperties = new RagLlmProperties();
        ragAiGatewayProperties = new RagAiGatewayProperties();

        ragEmbeddingProperties.setProvider("rag-ai-service");
        ragEmbeddingProperties.setModel("text-embedding-v4");

        ragLlmProperties.getChat().setModel("deepseek-v4-pro");
        ragAiGatewayProperties.setBaseUrl("http://localhost:8001");

        systemHealthService = new SystemHealthService(
                environment,
                jdbcTemplate,
                stringRedisTemplate,
                ragEmbeddingProperties,
                ragLlmProperties,
                ragAiGatewayProperties,
                aiGatewayClient
        );
    }

    @Test
    void currentStatusShouldIncludeEmbeddingAndLlmComponents() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(aiGatewayClient.createEmbedding(
                ragEmbeddingProperties.getModel(),
                "health check"
        )).thenReturn(List.of(0.1D, 0.2D));
        doNothing()
                .when(aiGatewayClient)
                .probeChatCompletion(
                        ragLlmProperties.getChat().getModel(),
                        0D,
                        64,
                        "You are a health check endpoint. Reply with exactly pong.",
                        "ping"
                );

        HealthStatusResponse response = systemHealthService.currentStatus();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.components()).containsKeys("postgres", "redis", "embedding", "llm");
        assertThat(response.components().get("embedding").status()).isEqualTo("UP");
        assertThat(response.components().get("embedding").provider()).isEqualTo("rag-ai-service");
        assertThat(response.components().get("llm").model()).isEqualTo("deepseek-v4-pro");
    }

    @Test
    void currentStatusShouldDegradeWhenEmbeddingProbeFails() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        doThrow(new RuntimeException("embedding timeout"))
                .when(aiGatewayClient)
                .probeEmbedding(anyString(), eq("health check"));
        doNothing()
                .when(aiGatewayClient)
                .probeChatCompletion(
                        ragLlmProperties.getChat().getModel(),
                        0D,
                        64,
                        "You are a health check endpoint. Reply with exactly pong.",
                        "ping"
                );

        HealthStatusResponse response = systemHealthService.currentStatus();

        assertThat(response.status()).isEqualTo("DEGRADED");
        assertThat(response.components().get("embedding").status()).isEqualTo("DOWN");
        assertThat(response.components().get("embedding").errorMessage()).contains("embedding timeout");
        assertThat(response.components().get("llm").status()).isEqualTo("UP");
    }

    private static class FixedPingStringRedisTemplate extends StringRedisTemplate {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(@NonNull RedisCallback<T> action) {
            return (T) "PONG";
        }
    }
}
