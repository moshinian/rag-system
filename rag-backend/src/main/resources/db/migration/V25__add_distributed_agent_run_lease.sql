ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS owner_instance_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;

UPDATE agent_run
SET lease_until = now()
WHERE status = 'RUNNING'
  AND lease_until IS NULL;

CREATE INDEX IF NOT EXISTS idx_agent_run_claim
    ON agent_run(status, created_at, id)
    WHERE status = 'QUEUED';

CREATE INDEX IF NOT EXISTS idx_agent_run_expired_lease
    ON agent_run(status, lease_until, id)
    WHERE status = 'RUNNING';
