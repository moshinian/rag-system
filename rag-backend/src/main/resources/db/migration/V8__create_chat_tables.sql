CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT PRIMARY KEY,
    session_code VARCHAR(64) NOT NULL UNIQUE,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    session_name VARCHAR(255),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY,
    message_code VARCHAR(64) NOT NULL UNIQUE,
    session_id BIGINT NOT NULL REFERENCES chat_session(id),
    message_type VARCHAR(32) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    retrieved_chunks TEXT,
    sources TEXT,
    prompt_template VARCHAR(128),
    model_name VARCHAR(128) NOT NULL,
    top_k INT NOT NULL,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_session_kb_created
    ON chat_session (knowledge_base_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_created
    ON chat_message (session_id, created_at DESC);
