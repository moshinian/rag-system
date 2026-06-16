CREATE TABLE IF NOT EXISTS agent_run (
    id BIGINT PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL UNIQUE,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    goal TEXT NOT NULL,
    question TEXT,
    run_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary TEXT,
    error_message VARCHAR(1024),
    created_by VARCHAR(128) NOT NULL,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agent_step (
    id BIGINT PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL REFERENCES agent_run(run_code),
    step_code VARCHAR(64) NOT NULL UNIQUE,
    node_name VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128),
    step_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    duration_ms BIGINT,
    error_message VARCHAR(1024),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agent_action (
    id BIGINT PRIMARY KEY,
    run_code VARCHAR(64) NOT NULL REFERENCES agent_run(run_code),
    action_code VARCHAR(64) NOT NULL UNIQUE,
    tool_name VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    reason TEXT,
    risk_level VARCHAR(32) NOT NULL,
    requires_confirmation BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    action_payload TEXT,
    confirmed_by VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    result_json TEXT,
    error_message VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_run_kb_created
    ON agent_run (knowledge_base_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_run_status
    ON agent_run (status);

CREATE INDEX IF NOT EXISTS idx_agent_step_run_created
    ON agent_step (run_code, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_agent_action_run_created
    ON agent_action (run_code, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_agent_action_status
    ON agent_action (status);
