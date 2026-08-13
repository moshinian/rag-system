package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagExecutorProperties;
import com.example.rag.config.RagIndexingProperties;
import com.example.rag.config.RagInstanceProperties;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import com.example.rag.common.id.SnowflakeWorkerIdAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在每个同构 Pod 中竞争 Claim，但只按本 Pod 并发额度领取任务。 */
@Component
public class IndexingTaskPoller {
    private static final String TASK_TYPE = "DOCUMENT_INDEXING";
    private static final Logger log = LoggerFactory.getLogger(IndexingTaskPoller.class);
    private final IndexingTaskRepository repository;
    private final IndexingTaskExecutionService executionService;
    private final RagIndexingProperties properties;
    private final RagInstanceProperties instanceProperties;
    private final Executor executor;
    private final Semaphore slots;
    private final SnowflakeWorkerIdAllocator workerIdAllocator;
    private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

    public IndexingTaskPoller(IndexingTaskRepository repository,
                              IndexingTaskExecutionService executionService,
                              RagIndexingProperties properties,
                              RagInstanceProperties instanceProperties,
                              RagExecutorProperties executorProperties,
                              SnowflakeWorkerIdAllocator workerIdAllocator,
                              @Qualifier("indexingExecutor") Executor executor) {
        this.repository = repository;
        this.executionService = executionService;
        this.properties = properties;
        this.instanceProperties = instanceProperties;
        this.executor = executor;
        this.workerIdAllocator = workerIdAllocator;
        this.slots = new Semaphore(Math.max(1, executorProperties.getMaxPoolSize()));
    }

    @Scheduled(fixedDelayString = "${rag.indexing.worker.poll-interval:1s}")
    public void poll() {
        if (!properties.getWorker().isEnabled() || !acceptingClaims.get()
                || !workerIdAllocator.canGenerateIds() || !slots.tryAcquire()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        IndexingTaskEntity claimed = repository.claimNext(TASK_TYPE, instanceProperties.instanceId(), now,
                        now.plus(properties.getWorker().getLeaseDuration()))
                .orElse(null);
        if (claimed == null) {
            slots.release();
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    executionService.execute(claimed);
                } finally {
                    slots.release();
                }
            });
        } catch (RejectedExecutionException ex) {
            slots.release();
            repository.returnOwnedToQueue(claimed, "Executor rejected claimed task", OffsetDateTime.now());
            log.warn(StructuredLogMessage.of("indexing.task.claim_rejected")
                    .field("taskId", claimed.getId())
                    .field("instanceId", instanceProperties.instanceId())
                    .build());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopClaiming() {
        acceptingClaims.set(false);
    }
}
