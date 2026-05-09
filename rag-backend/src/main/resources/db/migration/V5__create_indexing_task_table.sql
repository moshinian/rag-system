CREATE TABLE IF NOT EXISTS indexing_task (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    document_id BIGINT NOT NULL REFERENCES document(id),
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parser_name VARCHAR(64),
    chunk_count INT,
    error_message VARCHAR(1024),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_indexing_task_document_created
    ON indexing_task (document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_indexing_task_status
    ON indexing_task (status);
