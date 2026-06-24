package com.example.rag.service;

import com.example.rag.config.RagAiGatewayProperties;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagLlmProperties;
import com.example.rag.integration.ai.AiGatewayClient;
import com.example.rag.model.response.HealthComponentStatusResponse;
import com.example.rag.model.response.HealthStatusResponse;
import com.example.rag.model.response.RedisProbeResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查服务。
 */
@Service
public class SystemHealthService {
    private static final int LLM_HEALTH_PROBE_MAX_TOKENS = 64;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final RagLlmProperties ragLlmProperties;
    private final RagAiGatewayProperties ragAiGatewayProperties;
    private final AiGatewayClient aiGatewayClient;

    /** 构造SystemHealthService。 */
    public SystemHealthService(Environment environment,
                               JdbcTemplate jdbcTemplate,
                               StringRedisTemplate stringRedisTemplate,
                               RagEmbeddingProperties ragEmbeddingProperties,
                               RagLlmProperties ragLlmProperties,
                               RagAiGatewayProperties ragAiGatewayProperties,
                               AiGatewayClient aiGatewayClient) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.ragLlmProperties = ragLlmProperties;
        this.ragAiGatewayProperties = ragAiGatewayProperties;
        this.aiGatewayClient = aiGatewayClient;
    }

    /** 返回服务当前状态及关键依赖组件状态。 */
    public HealthStatusResponse currentStatus() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        Map<String, HealthComponentStatusResponse> components = new LinkedHashMap<>();

        // 这里固定输出顺序，便于前端健康页和日志对比时保持稳定展示。
        components.put("postgres", databaseStatus());
        components.put("redis", redisStatus());
        components.put("aiGateway", aiGatewayStatus());
        components.put("embedding", embeddingStatus());
        components.put("llm", llmStatus());

        boolean hasDownComponent = components.values().stream()
                .anyMatch(component -> "DOWN".equals(component.status()));
        String overallStatus = hasDownComponent ? "DEGRADED" : "UP";
        return new HealthStatusResponse("UP".equals(overallStatus) ? "UP" : "DEGRADED",
                "rag-service",
                activeProfiles,
                components,
                Instant.now());
    }

    /** 执行一次最小 Redis 读写探针。 */
    public RedisProbeResponse probeRedis() {
        String key = "rag:health:probe";
        String value = "ok-" + Instant.now().truncatedTo(ChronoUnit.MILLIS);
        stringRedisTemplate.opsForValue().set(key, value);
        // 这里显式回读刚写入的值，验证的不只是连通性，还包括最小读写闭环是否正常。
        String cachedValue = stringRedisTemplate.opsForValue().get(key);
        return new RedisProbeResponse(key, value, cachedValue, value.equals(cachedValue));
    }

    /** 检查数据库连通状态。 */
    private HealthComponentStatusResponse databaseStatus() {
        long startedAt = System.nanoTime();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean healthy = Integer.valueOf(1).equals(result);
            return new HealthComponentStatusResponse(
                    healthy ? "UP" : "DOWN",
                    "infrastructure",
                    "jdbc:postgresql",
                    null,
                    null,
                    elapsedMillis(startedAt),
                    healthy ? "SELECT 1 succeeded" : "SELECT 1 returned unexpected value",
                    healthy ? null : "Unexpected database probe result",
                    Instant.now()
            );
        } catch (DataAccessException exception) {
            return new HealthComponentStatusResponse(
                    "DOWN",
                    "infrastructure",
                    "jdbc:postgresql",
                    null,
                    null,
                    elapsedMillis(startedAt),
                    "SELECT 1 failed",
                    exception.getMessage(),
                    Instant.now()
            );
        }
    }

    /** 检查 Redis 连通状态。 */
    private HealthComponentStatusResponse redisStatus() {
        long startedAt = System.nanoTime();
        try {
            String pong = stringRedisTemplate.execute((RedisConnection connection) -> connection.ping());
            boolean healthy = "PONG".equalsIgnoreCase(pong);
            return new HealthComponentStatusResponse(
                    healthy ? "UP" : "DOWN",
                    "infrastructure",
                    "redis://ping",
                    null,
                    null,
                    elapsedMillis(startedAt),
                    healthy ? "PING/PONG succeeded" : "PING returned unexpected value: " + pong,
                    healthy ? null : "Unexpected Redis ping result",
                    Instant.now()
            );
        } catch (Exception exception) {
            return new HealthComponentStatusResponse(
                    "DOWN",
                    "infrastructure",
                    "redis://ping",
                    null,
                    null,
                    elapsedMillis(startedAt),
                    "Redis ping failed",
                    exception.getMessage(),
                    Instant.now()
            );
        }
    }

    /** 检查 embedding 接口是否可返回真实向量。 */
    private HealthComponentStatusResponse aiGatewayStatus() {
        long startedAt = System.nanoTime();
        try {
            AiGatewayClient.GatewayHealthSnapshot snapshot = aiGatewayClient.probeGatewayHealth();
            boolean healthy = "UP".equalsIgnoreCase(snapshot.status());
            return new HealthComponentStatusResponse(
                    healthy ? "UP" : "DOWN",
                    "ai-gateway",
                    aiGatewayClient.gatewayHealthEndpoint(),
                    "rag-ai-service",
                    null,
                    elapsedMillis(startedAt),
                    "AI gateway /health returned status=" + snapshot.status()
                            + ", embeddingProvider=" + nullSafe(snapshot.embeddingProvider())
                            + ", chatProvider=" + nullSafe(snapshot.chatProvider()),
                    healthy ? null : "Unexpected AI gateway health status: " + snapshot.status(),
                    Instant.now()
            );
        } catch (Exception exception) {
            return new HealthComponentStatusResponse(
                    "DOWN",
                    "ai-gateway",
                    aiGatewayClient.gatewayHealthEndpoint(),
                    "rag-ai-service",
                    null,
                    elapsedMillis(startedAt),
                    "AI gateway health request failed",
                    exception.getMessage(),
                    Instant.now()
            );
        }
    }

    /** 检查 embedding 接口是否可返回真实向量。 */
    private HealthComponentStatusResponse embeddingStatus() {
        long startedAt = System.nanoTime();
        String provider = "rag-ai-service";
        String model = ragEmbeddingProperties.getModel();
        try {
            AiGatewayClient.GatewayHealthSnapshot snapshot = aiGatewayClient.probeGatewayHealth();
            provider = hasText(snapshot.embeddingProvider()) ? snapshot.embeddingProvider() : provider;
            model = hasText(snapshot.embeddingDefaultModel()) ? snapshot.embeddingDefaultModel() : model;
            // embedding 健康检查要求真实返回向量，不只检查 HTTP 200。
            aiGatewayClient.probeEmbedding(
                    ragEmbeddingProperties.getModel(),
                    "health check"
            );
            return new HealthComponentStatusResponse(
                    "UP",
                    "ai-capability",
                    joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getEmbeddingsPath()),
                    provider,
                    model,
                    elapsedMillis(startedAt),
                    "Embedding request returned a vector",
                    null,
                    Instant.now()
            );
        } catch (Exception exception) {
            return new HealthComponentStatusResponse(
                    "DOWN",
                    "ai-capability",
                    joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getEmbeddingsPath()),
                    provider,
                    model,
                    elapsedMillis(startedAt),
                    "Embedding request failed",
                    exception.getMessage(),
                    Instant.now()
            );
        }
    }

    /** 检查 chat completion 接口是否可返回最小回答。 */
    private HealthComponentStatusResponse llmStatus() {
        long startedAt = System.nanoTime();
        RagLlmProperties.ChatProperties chat = ragLlmProperties.getChat();
        String provider = "rag-ai-service";
        String model = chat.getModel();
        try {
            AiGatewayClient.GatewayHealthSnapshot snapshot = aiGatewayClient.probeGatewayHealth();
            provider = hasText(snapshot.chatProvider()) ? snapshot.chatProvider() : provider;
            model = hasText(snapshot.chatDefaultModel()) ? snapshot.chatDefaultModel() : model;
            // LLM 探针只要求最小 completion 合法返回，避免健康检查本身消耗过多 token。
            aiGatewayClient.probeChatCompletion(
                    chat.getModel(),
                    0D,
                    resolveLlmProbeMaxTokens(chat.getMaxOutputTokens()),
                    "You are a health check endpoint. Reply with exactly pong.",
                    "ping"
            );
            return new HealthComponentStatusResponse(
                    "UP",
                    "ai-capability",
                    joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getChatCompletionsPath()),
                    provider,
                    model,
                    elapsedMillis(startedAt),
                    "Chat completion request returned a response",
                    null,
                    Instant.now()
            );
        } catch (Exception exception) {
            return new HealthComponentStatusResponse(
                    "DOWN",
                    "ai-capability",
                    joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getChatCompletionsPath()),
                    provider,
                    model,
                    elapsedMillis(startedAt),
                    "Chat completion request failed",
                    exception.getMessage(),
                    Instant.now()
            );
        }
    }

    /** 判断健康探针配置是否包含非空白文本。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    /** 把健康展示中的空文本转换为占位符。 */
    private String nullSafe(String value) {
        return hasText(value) ? value : "-";
    }

    /** 计算耗时毫秒数。 */
    private long elapsedMillis(long startedAt) {
        return Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    /** 解析 LLM 探针的最大输出 token 数。 */
    private int resolveLlmProbeMaxTokens(Integer configuredMaxOutputTokens) {
        if (configuredMaxOutputTokens == null || configuredMaxOutputTokens < 1) {
            return LLM_HEALTH_PROBE_MAX_TOKENS;
        }
        // 健康探针不放大业务配置的输出上限，始终收敛到一个很小的安全值。
        return Math.min(configuredMaxOutputTokens, LLM_HEALTH_PROBE_MAX_TOKENS);
    }

    /** 拼接基础地址和路径。 */
    private String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedBaseUrl.isEmpty()) {
            return normalizedPath;
        }
        if (normalizedPath.isEmpty()) {
            return normalizedBaseUrl;
        }
        if (normalizedBaseUrl.endsWith("/") && normalizedPath.startsWith("/")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) + normalizedPath;
        }
        if (!normalizedBaseUrl.endsWith("/") && !normalizedPath.startsWith("/")) {
            return normalizedBaseUrl + "/" + normalizedPath;
        }
        return normalizedBaseUrl + normalizedPath;
    }
}
