package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * 定时收敛长时间卡在 RUNNING 的 Agent run。
 */
@Component
public class AgentRunRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(AgentRunRecoveryScheduler.class);
    private final RagAgentProperties agentProperties;
    private final AgentRunLeaseRecoveryCoordinator recoveryCoordinator;

    /** 构造 AgentRunRecoveryScheduler。 */
    public AgentRunRecoveryScheduler(RagAgentProperties agentProperties,
                                     AgentRunLeaseRecoveryCoordinator recoveryCoordinator) {
        this.agentProperties = agentProperties;
        this.recoveryCoordinator = recoveryCoordinator;
    }

    /** 周期性扫描同时缺少 heartbeat 与业务事件的 RUNNING run。 */
    @Scheduled(fixedDelayString = "#{${rag.agent.recovery.scan-interval-seconds:60} * 1000}")
    public void recoverStaleRuns() {
        if (!agentProperties.getRecovery().isEnabled()) {
            return;
        }

        int recovered = 0;
        for (int i = 0; i < 100; i++) {
            if (recoveryCoordinator.recoverOne().isEmpty()) {
                break;
            }
            recovered++;
        }
        if (recovered > 0) {
            log.info(StructuredLogMessage.of("agent.run.recovery.completed")
                    .field("runCount", recovered)
                    .build());
        }
    }
}
