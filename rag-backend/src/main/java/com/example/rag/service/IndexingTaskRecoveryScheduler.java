package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagIndexingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 所有 Pod 都运行；数据库 SKIP LOCKED 保证同一过期任务只恢复一次。 */
@Component
public class IndexingTaskRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(IndexingTaskRecoveryScheduler.class);
    private final IndexingTaskRecoveryCoordinator coordinator;
    private final RagIndexingProperties properties;

    public IndexingTaskRecoveryScheduler(IndexingTaskRecoveryCoordinator coordinator,
                                         RagIndexingProperties properties) {
        this.coordinator = coordinator;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${rag.indexing.recovery.scan-interval:10s}")
    public void recover() {
        if (!properties.getRecovery().isEnabled()) {
            return;
        }
        int recovered = 0;
        for (int i = 0; i < Math.max(1, properties.getRecovery().getScanLimit()); i++) {
            if (coordinator.recoverOne().isEmpty()) {
                break;
            }
            recovered++;
        }
        if (recovered > 0) {
            log.info(StructuredLogMessage.of("indexing.recovery.completed")
                    .field("taskCount", recovered)
                    .build());
        }
    }
}
