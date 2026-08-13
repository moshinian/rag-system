package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RagIndexingProperties;
import com.example.rag.model.enums.IndexingTaskStage;
import com.example.rag.model.enums.IndexingTaskStatus;
import com.example.rag.model.enums.IndexingTaskTriggerSource;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 单事务锁定一条过期任务并创建唯一恢复子任务。 */
@Service
public class IndexingTaskRecoveryCoordinator {
    private static final String TASK_TYPE = "DOCUMENT_INDEXING";
    private final IndexingTaskRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final RagIndexingProperties properties;

    public IndexingTaskRecoveryCoordinator(IndexingTaskRepository repository,
                                           SnowflakeIdGenerator idGenerator,
                                           RagIndexingProperties properties) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.properties = properties;
    }

    @Transactional
    public Optional<Long> recoverOne() {
        OffsetDateTime now = OffsetDateTime.now();
        IndexingTaskEntity source = repository.lockNextExpired(TASK_TYPE, now).orElse(null);
        if (source == null) {
            return Optional.empty();
        }
        int retryCount = source.getRetryCount() == null ? 0 : source.getRetryCount();
        int maxRetry = source.getMaxRetryCount() == null
                ? properties.getMaxRetryCount() : source.getMaxRetryCount();
        source.setStatus(IndexingTaskStatus.FAILED);
        source.setFinishedAt(now);
        source.setRecoveredAt(now);
        source.setLeaseUntil(null);
        if (retryCount >= maxRetry) {
            source.setErrorMessage("Task lease expired and max retry count was reached");
            repository.updateById(source);
            return Optional.of(source.getId());
        }

        IndexingTaskEntity child = new IndexingTaskEntity();
        child.setId(idGenerator.nextId());
        child.setKnowledgeBaseId(source.getKnowledgeBaseId());
        child.setDocumentId(source.getDocumentId());
        child.setParentTaskId(source.getId());
        child.setTaskType(TASK_TYPE);
        child.setStatus(IndexingTaskStatus.QUEUED);
        child.setTaskStage(IndexingTaskStage.QUEUED);
        child.setTriggerSource(IndexingTaskTriggerSource.RECOVERY);
        child.setRetryCount(retryCount + 1);
        child.setMaxRetryCount(maxRetry);
        child.setCreatedBy(source.getCreatedBy());
        child.setLeaseVersion(0L);

        source.setErrorMessage("Recovered by task " + child.getId());
        repository.updateById(source);
        repository.insert(child);
        return Optional.of(child.getId());
    }
}
