package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAgentProperties;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 持久化 Agent Runtime heartbeat。
 */
@Service
public class AgentRunHeartbeatService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunHeartbeatService.class);

    private final AgentRunRepository runRepository;
    private final RagAgentProperties agentProperties;
    private final ConcurrentMap<String, Instant> lastPersistedAt = new ConcurrentHashMap<>();

    /** 构造 AgentRunHeartbeatService。 */
    public AgentRunHeartbeatService(AgentRunRepository runRepository,
                                    RagAgentProperties agentProperties) {
        this.runRepository = runRepository;
        this.agentProperties = agentProperties;
    }

    /**
     * 轻量更新 Runtime heartbeat。
     *
     * <p>失败只记录日志，不中断 Python stream；Recovery 会同时参考业务事件，避免误判。</p>
     */
    public void touchRuntimeHeartbeat(String runCode) {
        if (runCode == null || runCode.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        long intervalSeconds = Math.max(1, agentProperties.getRecovery().getHeartbeatUpdateIntervalSeconds());
        Instant previous = lastPersistedAt.get(runCode);
        if (previous != null && Duration.between(previous, now).getSeconds() < intervalSeconds) {
            return;
        }

        try {
            int updated = runRepository.updateRuntimeHeartbeatToNow(runCode);
            if (updated == 1) {
                lastPersistedAt.put(runCode, now);
            }
        } catch (RuntimeException ex) {
            log.warn(StructuredLogMessage.of("agent.run.heartbeat_update_failed")
                    .field("runCode", runCode)
                    .field("message", ex.getMessage())
                    .build(), ex);
        }
    }

    public boolean touchOwnedHeartbeat(AgentRunEntity run) {
        OffsetDateTime now = OffsetDateTime.now();
        return runRepository.heartbeatOwned(run.getRunCode(), run.getOwnerInstanceId(), run.getLeaseVersion(),
                now, now.plus(agentProperties.getWorker().getLeaseDuration()));
    }

    /** run 进入终态或后台任务结束后清理节流记录，避免内存长期积累。 */
    public void cleanup(String runCode) {
        if (runCode != null) {
            lastPersistedAt.remove(runCode);
        }
    }
}
