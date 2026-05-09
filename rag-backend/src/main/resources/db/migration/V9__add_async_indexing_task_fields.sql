ALTER TABLE indexing_task
    ADD COLUMN IF NOT EXISTS task_stage VARCHAR(64) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN IF NOT EXISTS embedded_chunk_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_indexing_task_document_type_created
    ON indexing_task (document_id, task_type, created_at DESC);
