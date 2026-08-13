ALTER TABLE indexing_task
    ADD COLUMN IF NOT EXISTS owner_instance_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_version BIGINT NOT NULL DEFAULT 0;

-- 版本升级前正在运行的进程没有合法 Owner。令其立即过期，交给新 Recovery 安全接管。
UPDATE indexing_task
SET lease_until = now()
WHERE status = 'RUNNING'
  AND lease_until IS NULL;

CREATE INDEX IF NOT EXISTS idx_indexing_task_claim
    ON indexing_task(status, task_type, created_at, id)
    WHERE status = 'QUEUED';

CREATE INDEX IF NOT EXISTS idx_indexing_task_expired_lease
    ON indexing_task(status, task_type, lease_until, id)
    WHERE status = 'RUNNING';
