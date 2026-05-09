package com.example.rag.service;

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

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public SystemHealthService(Environment environment,
                               JdbcTemplate jdbcTemplate,
                               StringRedisTemplate stringRedisTemplate) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 返回服务当前状态及关键依赖组件状态。 */
    public HealthStatusResponse currentStatus() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        Map<String, String> components = new LinkedHashMap<>();

        components.put("postgres", databaseStatus());
        components.put("redis", redisStatus());

        String overallStatus = components.containsValue("DOWN") ? "DEGRADED" : "UP";
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
        String cachedValue = stringRedisTemplate.opsForValue().get(key);
        return new RedisProbeResponse(key, value, cachedValue, value.equals(cachedValue));
    }

    /** 检查数据库连通状态。 */
    private String databaseStatus() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result) ? "UP" : "DOWN";
        } catch (DataAccessException exception) {
            return "DOWN";
        }
    }

    /** 检查 Redis 连通状态。 */
    private String redisStatus() {
        try {
            String pong = stringRedisTemplate.execute((RedisConnection connection) -> connection.ping());
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception exception) {
            return "DOWN";
        }
    }
}
