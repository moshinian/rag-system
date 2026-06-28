package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAgentProperties;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 定时收敛长时间卡在 RUNNING 的 Agent run。
 */
@Component
public class AgentRunRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(AgentRunRecoveryScheduler.class);
    private static final int SCAN_LIMIT = 100;

    private final RagAgentProperties agentProperties;
    private final AgentRunRepository runRepository;
    private final AgentRunRecoveryService recoveryService;

    /** 构造 AgentRunRecoveryScheduler。 */
    public AgentRunRecoveryScheduler(RagAgentProperties agentProperties,
                                     AgentRunRepository runRepository,
                                     AgentRunRecoveryService recoveryService) {
        this.agentProperties = agentProperties;
        this.runRepository = runRepository;
        this.recoveryService = recoveryService;
    }

    /** 周期性扫描同时缺少 heartbeat 与业务事件的 RUNNING run。 */
    @Scheduled(fixedDelayString = "#{${rag.agent.recovery.scan-interval-seconds:60} * 1000}")
    public void recoverStaleRuns() {
        if (!agentProperties.getRecovery().isEnabled()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime runningCutoff = now.minusMinutes(
                Math.max(1, agentProperties.getRecovery().getRunningTimeoutMinutes()));
        OffsetDateTime idleCutoff = now.minusMinutes(
                Math.max(1, agentProperties.getRecovery().getIdleTimeoutMinutes()));
        List<AgentRunEntity> candidates = runRepository.findRecoverableRunningRuns(
                runningCutoff,
                idleCutoff,
                SCAN_LIMIT
        );
        if (!candidates.isEmpty()) {
            log.info(StructuredLogMessage.of("agent.run.recovery.scan_found")
                    .field("runCount", candidates.size())
                    .field("runningCutoff", runningCutoff)
                    .field("idleCutoff", idleCutoff)
                    .build());
        }

        for (AgentRunEntity candidate : candidates) {
            try {
                recoveryService.recoverOne(candidate, idleCutoff);
            } catch (RuntimeException ex) {
                // 单条恢复失败不能中断整轮扫描，下一轮仍可继续兜底。
                log.warn(StructuredLogMessage.of("agent.run.recovery.scan_failed")
                        .field("runCode", candidate.getRunCode())
                        .field("message", ex.getMessage())
                        .build(), ex);
            }
        }
    }
}
