package com.example.rag.service;

import com.example.rag.config.RagAgentProperties;
import com.example.rag.model.dto.AgentRunEventDraft;
import com.example.rag.model.enums.AgentRunEventType;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 锁定过期 Agent Lease；可重试则回队列，超限则生成持久化终态事件。 */
@Service
public class AgentRunLeaseRecoveryCoordinator {
    private final AgentRunRepository repository;
    private final AgentRunEventService eventService;
    private final RagAgentProperties properties;

    public AgentRunLeaseRecoveryCoordinator(AgentRunRepository repository,
                                            AgentRunEventService eventService,
                                            RagAgentProperties properties) {
        this.repository = repository;
        this.eventService = eventService;
        this.properties = properties;
    }

    @Transactional
    public Optional<String> recoverOne() {
        AgentRunEntity run = repository.lockNextExpired(OffsetDateTime.now()).orElse(null);
        if (run == null) {
            return Optional.empty();
        }
        int attempts = run.getAttemptCount() == null ? 0 : run.getAttemptCount();
        if (attempts < Math.max(1, properties.getWorker().getMaxAttempts())) {
            run.setStatus(AgentRunStatus.QUEUED);
            run.setOwnerInstanceId(null);
            run.setClaimedAt(null);
            run.setLeaseUntil(null);
            run.setRuntimeHeartbeatAt(null);
            run.setErrorMessage("Previous worker lease expired; queued for recovery");
            repository.updateById(run);
            return Optional.of(run.getRunCode());
        }
        String message = "Agent run lease expired and max attempts were reached";
        run.setStatus(AgentRunStatus.FAILED);
        run.setLeaseUntil(null);
        run.setFinishedAt(OffsetDateTime.now());
        run.setErrorMessage(message);
        repository.updateById(run);
        eventService.persist(new AgentRunEventDraft(null, run.getRunCode(), null,
                AgentRunEventType.RUN_FAILED, null, null, AgentRunStatus.FAILED.name(), message,
                "{\"source\":\"LEASE_RECOVERY\"}"));
        return Optional.of(run.getRunCode());
    }
}
