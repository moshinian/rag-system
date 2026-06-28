ALTER TABLE agent_step
    ADD COLUMN IF NOT EXISTS node_invocation_id VARCHAR(96);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_step_run_invocation
    ON agent_step (run_code, node_invocation_id)
    WHERE node_invocation_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS agent_run_event (
    id BIGINT PRIMARY KEY,
    event_code VARCHAR(64) NOT NULL,
    run_code VARCHAR(64) NOT NULL REFERENCES agent_run(run_code),
    node_invocation_id VARCHAR(96),
    event_type VARCHAR(64) NOT NULL,
    node_name VARCHAR(128),
    tool_name VARCHAR(128),
    status VARCHAR(32),
    message TEXT,
    payload_json TEXT,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_event_code
    ON agent_run_event (event_code);

CREATE INDEX IF NOT EXISTS idx_agent_run_event_run_id
    ON agent_run_event (run_code, id);
