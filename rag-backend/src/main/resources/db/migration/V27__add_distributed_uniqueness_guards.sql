-- 同一文档只允许存在一条集群级活动索引任务，避免两个 API Pod 并发提交。
CREATE UNIQUE INDEX IF NOT EXISTS uq_indexing_task_active_document
    ON indexing_task (document_id, task_type)
    WHERE task_type = 'DOCUMENT_INDEXING' AND status IN ('QUEUED', 'RUNNING');

-- At-Least-Once 重放时，文档内 chunk 序号是稳定业务幂等键。
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_chunk_document_index
    ON document_chunk (document_id, chunk_index);

-- Rebuild 提交的先查后插只能作为友好提示，最终并发不变量由数据库保证。
CREATE UNIQUE INDEX IF NOT EXISTS uq_embedding_rebuild_single_active
    ON embedding_rebuild_run ((1))
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING');
