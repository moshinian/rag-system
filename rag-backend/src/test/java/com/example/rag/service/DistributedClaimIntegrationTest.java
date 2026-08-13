package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 PostgreSQL 验证 SKIP LOCKED Claim 与 Recovery 事务边界。 */
@SpringBootTest(properties = {
        "rag.indexing.worker.enabled=false",
        "rag.indexing.recovery.enabled=false",
        "rag.agent.worker.enabled=false",
        "rag.agent.recovery.enabled=false"
})
class DistributedClaimIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SnowflakeIdGenerator idGenerator;
    @Autowired
    private IndexingTaskRepository indexingRepository;
    @Autowired
    private AgentRunRepository agentRunRepository;
    @Autowired
    private IndexingTaskRecoveryCoordinator recoveryCoordinator;

    private long knowledgeBaseId;
    private long documentId;
    private long taskId;
    private String runCode;

    @BeforeEach
    void setUp() {
        knowledgeBaseId = idGenerator.nextId();
        documentId = idGenerator.nextId();
        taskId = idGenerator.nextId();
        runCode = "AR-CLAIM-" + idGenerator.nextId();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO knowledge_base(id, kb_code, name, status, created_by, created_at, updated_at)
                VALUES (?, ?, 'claim-test', 'ACTIVE', 'test', ?, ?)
                """, knowledgeBaseId, "claim-kb-" + knowledgeBaseId, now, now);
        jdbc.update("""
                INSERT INTO document(id, knowledge_base_id, document_code, file_name, display_name, file_type,
                    media_type, storage_path, storage_type, file_size, content_hash, status, version,
                    created_by, created_at, updated_at)
                VALUES (?, ?, ?, 'a.md', 'a', 'md', 'text/markdown', '/tmp/a.md', 'local', 1,
                    ?, 'UPLOADED', 1, 'test', ?, ?)
                """, documentId, knowledgeBaseId, "DOC-" + documentId, "hash-" + documentId, now, now);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM agent_run_event WHERE run_code = ?", runCode);
        jdbc.update("DELETE FROM agent_run WHERE knowledge_base_id = ?", knowledgeBaseId);
        jdbc.update("DELETE FROM indexing_task WHERE document_id = ?", documentId);
        jdbc.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
        jdbc.update("DELETE FROM document WHERE id = ?", documentId);
        jdbc.update("DELETE FROM knowledge_base WHERE id = ?", knowledgeBaseId);
    }

    @Test
    void fourWorkersShouldClaimSingleIndexingTaskOnlyOnce() throws Exception {
        insertQueuedTask();
        List<Optional<IndexingTaskEntity>> results = race(4, index -> indexingRepository.claimNext(
                "DOCUMENT_INDEXING", "pod-" + index, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(2)));

        assertThat(results.stream().flatMap(Optional::stream)).hasSize(1);
        IndexingTaskEntity persisted = indexingRepository.findById(taskId).orElseThrow();
        assertThat(persisted.getOwnerInstanceId()).startsWith("pod-");
        assertThat(persisted.getLeaseVersion()).isEqualTo(1L);
    }

    @Test
    void fourWorkersShouldClaimSingleAgentRunOnlyOnce() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO agent_run(id, run_code, knowledge_base_id, goal, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, 'diagnose', 'QUEUED', 'test', ?, ?)
                """, idGenerator.nextId(), runCode, knowledgeBaseId, now, now);

        List<Optional<AgentRunEntity>> results = race(4, index -> agentRunRepository.claimNext(
                "pod-" + index, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(2)));

        assertThat(results.stream().flatMap(Optional::stream)).hasSize(1);
        AgentRunEntity persisted = agentRunRepository.findByRunCode(runCode).orElseThrow();
        assertThat(persisted.getOwnerInstanceId()).startsWith("pod-");
        assertThat(persisted.getLeaseVersion()).isEqualTo(1L);
    }

    @Test
    void fourRecoverySchedulersShouldCreateOneRetryChild() throws Exception {
        insertExpiredRunningTask();

        race(4, ignored -> recoveryCoordinator.recoverOne());

        Integer childCount = jdbc.queryForObject(
                "SELECT count(*) FROM indexing_task WHERE parent_task_id = ?", Integer.class, taskId);
        assertThat(childCount).isEqualTo(1);
    }

    private void insertQueuedTask() {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO indexing_task(id, knowledge_base_id, document_id, task_type, status, task_stage,
                    trigger_source, retry_count, max_retry_count, created_by, created_at, updated_at)
                VALUES (?, ?, ?, 'DOCUMENT_INDEXING', 'QUEUED', 'QUEUED', 'SUBMIT', 0, 3, 'test', ?, ?)
                """, taskId, knowledgeBaseId, documentId, now, now);
    }

    private void insertExpiredRunningTask() {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO indexing_task(id, knowledge_base_id, document_id, task_type, status, task_stage,
                    trigger_source, retry_count, max_retry_count, owner_instance_id, claimed_at, lease_until,
                    lease_version, started_at, last_heartbeat_at, created_by, created_at, updated_at)
                VALUES (?, ?, ?, 'DOCUMENT_INDEXING', 'RUNNING', 'DOCUMENT_PROCESSING', 'SUBMIT', 0, 3,
                    'dead-pod', ?, ?, 1, ?, ?, 'test', ?, ?)
                """, taskId, knowledgeBaseId, documentId, now.minusMinutes(3), now.minusMinutes(1),
                now.minusMinutes(3), now.minusMinutes(3), now.minusMinutes(3), now.minusMinutes(3));
    }

    private <T> List<T> race(int workers, ThrowingFunction<Integer, T> operation) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return operation.apply(index);
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T value) throws Exception;
    }
}
