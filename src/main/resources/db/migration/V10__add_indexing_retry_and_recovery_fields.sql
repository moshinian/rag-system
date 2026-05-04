ALTER TABLE indexing_task
    ADD COLUMN IF NOT EXISTS parent_task_id BIGINT REFERENCES indexing_task(id),
    ADD COLUMN IF NOT EXISTS trigger_source VARCHAR(32) NOT NULL DEFAULT 'SUBMIT',
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_retry_count INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS recovered_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_indexing_task_status_heartbeat
    ON indexing_task (status, last_heartbeat_at);
