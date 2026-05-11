package com.example.rag.controller;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RequestIdFilter;
import com.example.rag.model.response.HealthComponentStatusResponse;
import com.example.rag.model.response.HealthStatusResponse;
import com.example.rag.model.response.RedisProbeResponse;
import com.example.rag.service.SystemHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private MockMvc mockMvc;
    private SystemHealthService systemHealthService;
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @BeforeEach
    void setUp() {
        systemHealthService = mock(SystemHealthService.class);
        snowflakeIdGenerator = mock(SnowflakeIdGenerator.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(systemHealthService))
                .addFilters(new RequestIdFilter(snowflakeIdGenerator))
                .build();
    }

    @Test
    void healthShouldExposeStructuredComponentStatuses() throws Exception {
        when(systemHealthService.currentStatus()).thenReturn(new HealthStatusResponse(
                "DEGRADED",
                "rag-service",
                List.of("local"),
                Map.of(
                        "postgres", new HealthComponentStatusResponse("UP", "infrastructure", "jdbc:postgresql", null, null, 12L, "SELECT 1 succeeded", null, Instant.parse("2026-05-11T10:00:00Z")),
                        "embedding", new HealthComponentStatusResponse("DOWN", "ai-capability", "https://example.test/embeddings", "aliyun", "text-embedding-v4", 123L, "Embedding request failed", "401 unauthorized", Instant.parse("2026-05-11T10:00:01Z"))
                ),
                Instant.parse("2026-05-11T10:00:02Z")
        ));
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.components.postgres.status").value("UP"))
                .andExpect(jsonPath("$.data.components.embedding.model").value("text-embedding-v4"))
                .andExpect(jsonPath("$.data.components.embedding.errorMessage").value("401 unauthorized"));
    }

    @Test
    void redisProbeShouldSupportGet() throws Exception {
        when(systemHealthService.probeRedis()).thenReturn(new RedisProbeResponse("probe-key", "ok", "ok", true));
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(get("/api/health/redis-probe"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.key").value("probe-key"))
                .andExpect(jsonPath("$.data.matched").value(true));
    }

    @Test
    void redisProbeShouldKeepSupportingPost() throws Exception {
        when(systemHealthService.probeRedis()).thenReturn(new RedisProbeResponse("probe-key", "ok", "ok", true));
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/health/redis-probe"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.key").value("probe-key"))
                .andExpect(jsonPath("$.data.matched").value(true));
    }
}
