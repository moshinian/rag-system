package com.example.rag.service;

import com.example.rag.config.RagAgentProperties;
import com.example.rag.config.RagInstanceProperties;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.common.id.SnowflakeWorkerIdAllocator;
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

/** 集群 Agent Worker Poller。 */
@Component
public class AgentRunPoller {
    private final AgentRunRepository runRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentRunExecutor runExecutor;
    private final RagAgentProperties properties;
    private final RagInstanceProperties instanceProperties;
    private final Executor executor;
    private final Semaphore slots;
    private final SnowflakeWorkerIdAllocator workerIdAllocator;
    private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

    public AgentRunPoller(AgentRunRepository runRepository,
                          KnowledgeBaseRepository knowledgeBaseRepository,
                          AgentRunExecutor runExecutor,
                          RagAgentProperties properties,
                          RagInstanceProperties instanceProperties,
                          SnowflakeWorkerIdAllocator workerIdAllocator,
                          @Qualifier("agentExecutor") Executor executor) {
        this.runRepository = runRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.runExecutor = runExecutor;
        this.properties = properties;
        this.instanceProperties = instanceProperties;
        this.executor = executor;
        this.workerIdAllocator = workerIdAllocator;
        this.slots = new Semaphore(Math.max(1, properties.getExecutor().getMaxPoolSize()));
    }

    @Scheduled(fixedDelayString = "${rag.agent.worker.poll-interval:1s}")
    public void poll() {
        if (!properties.getWorker().isEnabled() || !acceptingClaims.get()
                || !workerIdAllocator.canGenerateIds() || !slots.tryAcquire()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        AgentRunEntity run = runRepository.claimNext(instanceProperties.instanceId(), now,
                now.plus(properties.getWorker().getLeaseDuration())).orElse(null);
        if (run == null) {
            slots.release();
            return;
        }
        String kbCode = knowledgeBaseRepository.findById(run.getKnowledgeBaseId())
                .orElseThrow(() -> new IllegalStateException("Knowledge base not found for run " + run.getRunCode()))
                .getKbCode();
        try {
            executor.execute(() -> {
                try {
                    runExecutor.executeClaimed(kbCode, run);
                } finally {
                    slots.release();
                }
            });
        } catch (RejectedExecutionException ex) {
            slots.release();
            runRepository.returnOwnedToQueue(run, "Agent executor rejected claimed run", OffsetDateTime.now());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopClaiming() {
        acceptingClaims.set(false);
    }
}
