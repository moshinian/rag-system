package com.example.rag.service.agent;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** MCP Streamable HTTP session 的跨 Pod Redis 状态。 */
@Service
public class McpSessionStore {
    private static final String PREFIX = "rag:mcp:session:";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final DefaultRedisScript<Long> INITIALIZE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 1 then "
                    + "redis.call('set', KEYS[1], 'initialized', 'PX', ARGV[1]); return 1 "
                    + "else return 0 end",
            Long.class
    );
    private final StringRedisTemplate redis;

    public McpSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create() {
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set(key(id), "created", TTL);
        return id;
    }

    public boolean exists(String sessionId) {
        String value = redis.opsForValue().get(key(sessionId));
        if (value != null) {
            redis.expire(key(sessionId), TTL);
        }
        return value != null;
    }

    public boolean initialized(String sessionId) {
        String value = redis.opsForValue().get(key(sessionId));
        if (value != null) {
            redis.expire(key(sessionId), TTL);
        }
        return "initialized".equals(value);
    }

    public boolean markInitialized(String sessionId) {
        Long updated = redis.execute(INITIALIZE_SCRIPT, List.of(key(sessionId)), String.valueOf(TTL.toMillis()));
        return Long.valueOf(1L).equals(updated);
    }

    public void delete(String sessionId) {
        redis.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return PREFIX + sessionId;
    }
}
