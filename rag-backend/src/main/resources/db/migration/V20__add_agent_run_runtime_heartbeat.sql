ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS runtime_heartbeat_at TIMESTAMPTZ;

UPDATE agent_run
SET runtime_heartbeat_at = now()
WHERE status = 'RUNNING'
  AND runtime_heartbeat_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_agent_run_status_created_heartbeat
    ON agent_run(status, created_at, runtime_heartbeat_at);
