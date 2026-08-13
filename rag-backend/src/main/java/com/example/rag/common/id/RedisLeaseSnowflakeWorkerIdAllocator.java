package com.example.rag.common.id;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagInstanceProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 通过带 token 的 Redis Lease 动态分配 Snowflake WorkerId。 */
@Component
@ConditionalOnProperty(prefix = "rag.id.worker-lease", name = "mode", havingValue = "redis-lease")
public class RedisLeaseSnowflakeWorkerIdAllocator implements SnowflakeWorkerIdAllocator, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(RedisLeaseSnowflakeWorkerIdAllocator.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class
    );
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final SnowflakeWorkerLeaseProperties properties;
    private final SnowflakeIdProperties idProperties;
    private final RagInstanceProperties instanceProperties;
    private final ApplicationContext applicationContext;
    private final String token;
    private volatile long allocatedWorkerId = -1;
    private volatile long validUntilNanos;
    private volatile boolean healthy;

    public RedisLeaseSnowflakeWorkerIdAllocator(StringRedisTemplate redis,
                                                SnowflakeWorkerLeaseProperties properties,
                                                SnowflakeIdProperties idProperties,
                                                RagInstanceProperties instanceProperties,
                                                ApplicationContext applicationContext) {
        this.redis = redis;
        this.properties = properties;
        this.idProperties = idProperties;
        this.instanceProperties = instanceProperties;
        this.applicationContext = applicationContext;
        this.token = instanceProperties.instanceId() + ":" + UUID.randomUUID();
    }

    @PostConstruct
    public void acquire() {
        validateDurations();
        int max = Math.min(31, properties.getMaxWorkerId());
        for (int candidate = 0; candidate <= max; candidate++) {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    key(candidate), token, properties.getLeaseDuration());
            if (Boolean.TRUE.equals(acquired)) {
                allocatedWorkerId = candidate;
                markRenewed();
                log.info(StructuredLogMessage.of("snowflake.worker_lease.acquired")
                        .field("instanceId", instanceProperties.instanceId())
                        .field("workerId", candidate)
                        .field("datacenterId", idProperties.datacenterId())
                        .build());
                return;
            }
        }
        throw new IllegalStateException("No Snowflake workerId lease is available in range 0.." + max);
    }

    @Scheduled(fixedDelayString = "${rag.id.worker-lease.renewal-interval:30s}")
    public void renew() {
        if (!healthy || allocatedWorkerId < 0) {
            return;
        }
        try {
            Long renewed = redis.execute(RENEW_SCRIPT, List.of(key(allocatedWorkerId)),
                    token, String.valueOf(properties.getLeaseDuration().toMillis()));
            if (Long.valueOf(1L).equals(renewed)) {
                markRenewed();
                return;
            }
            loseLease("token_mismatch");
        } catch (RuntimeException ex) {
            if (System.nanoTime() >= validUntilNanos) {
                loseLease("renewal_unavailable_after_expiry");
            } else {
                log.warn(StructuredLogMessage.of("snowflake.worker_lease.renew_failed")
                        .field("instanceId", instanceProperties.instanceId())
                        .field("workerId", allocatedWorkerId)
                        .field("message", ex.getMessage())
                        .build());
            }
        }
    }

    @PreDestroy
    public synchronized void release() {
        if (allocatedWorkerId < 0) {
            return;
        }
        healthy = false;
        try {
            Long released = redis.execute(RELEASE_SCRIPT, List.of(key(allocatedWorkerId)), token);
            log.info(StructuredLogMessage.of("snowflake.worker_lease.released")
                    .field("instanceId", instanceProperties.instanceId())
                    .field("workerId", allocatedWorkerId)
                    .field("released", Long.valueOf(1L).equals(released))
                    .build());
            allocatedWorkerId = -1;
        } catch (RuntimeException ex) {
            log.warn(StructuredLogMessage.of("snowflake.worker_lease.release_failed")
                    .field("instanceId", instanceProperties.instanceId())
                    .field("workerId", allocatedWorkerId)
                    .field("message", ex.getMessage())
                    .build());
        }
    }

    /**
     * 低 phase 在业务 Executor 完成停止与在途任务 drain 之后执行，避免过早释放
     * workerId；同时仍早于普通 Bean destroy，Redis 连接可用于 Lua 安全释放。
     */
    @Override
    public void stop() {
        release();
    }

    @Override
    public void start() {
        // Lease 已在 PostConstruct 获取，Lifecycle start 无需重复分配。
    }

    @Override
    public boolean isRunning() {
        return allocatedWorkerId >= 0;
    }

    @Override
    public int getPhase() {
        return -1000;
    }

    @Override
    public long workerId() {
        if (!canGenerateIds()) {
            throw new IllegalStateException("Snowflake workerId lease is not valid");
        }
        return allocatedWorkerId;
    }

    @Override
    public boolean canGenerateIds() {
        if (healthy && System.nanoTime() >= validUntilNanos) {
            loseLease("local_lease_deadline_elapsed");
        }
        return healthy;
    }

    @Override
    public String description() {
        return "redis-lease:" + allocatedWorkerId + ":" + instanceProperties.instanceId();
    }

    private void markRenewed() {
        validUntilNanos = System.nanoTime() + properties.getLeaseDuration().toNanos();
        healthy = true;
    }

    private synchronized void loseLease(String reason) {
        if (!healthy) {
            return;
        }
        healthy = false;
        log.error(StructuredLogMessage.of("snowflake.worker_lease.lost")
                .field("instanceId", instanceProperties.instanceId())
                .field("workerId", allocatedWorkerId)
                .field("reason", reason)
                .build());
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
    }

    private String key(long workerId) {
        return properties.getKeyPrefix() + ":" + idProperties.datacenterId() + ":" + workerId;
    }

    private void validateDurations() {
        Duration lease = properties.getLeaseDuration();
        Duration renewal = properties.getRenewalInterval();
        if (lease == null || renewal == null || lease.isZero() || lease.isNegative()
                || renewal.isZero() || renewal.isNegative() || !renewal.minus(lease).isNegative()) {
            throw new IllegalArgumentException("Snowflake renewal interval must be positive and shorter than lease duration");
        }
    }
}
