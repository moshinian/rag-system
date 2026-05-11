ALTER TABLE document_chunk
    ALTER COLUMN embedding_vector TYPE vector USING embedding_vector::vector;

ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(128),
    ADD COLUMN IF NOT EXISTS embedding_profile_fingerprint VARCHAR(128),
    ADD COLUMN IF NOT EXISTS embedding_rebuild_run_id BIGINT,
    ADD COLUMN IF NOT EXISTS embedding_updated_by VARCHAR(128);

CREATE TABLE IF NOT EXISTS embedding_configuration_state (
    id BIGINT PRIMARY KEY,
    current_config_fingerprint VARCHAR(128) NOT NULL,
    active_config_fingerprint VARCHAR(128) NOT NULL,
    active_embedding_model VARCHAR(128),
    reembed_required BOOLEAN NOT NULL DEFAULT FALSE,
    rebuild_run_id BIGINT,
    reembed_confirmed_by VARCHAR(128),
    reembed_confirmed_at TIMESTAMPTZ,
    reembed_started_at TIMESTAMPTZ,
    reembed_finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS embedding_rebuild_run (
    id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    target_fingerprint VARCHAR(128) NOT NULL,
    target_model VARCHAR(128) NOT NULL,
    target_provider VARCHAR(128) NOT NULL,
    vector_dimensions INTEGER NOT NULL,
    distance_metric VARCHAR(64) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    total_document_count INTEGER NOT NULL DEFAULT 0,
    succeeded_document_count INTEGER NOT NULL DEFAULT 0,
    failed_document_count INTEGER NOT NULL DEFAULT 0,
    error_summary VARCHAR(1024),
    last_heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding_profile_fingerprint
    ON document_chunk (embedding_profile_fingerprint);

CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding_rebuild_run_id
    ON document_chunk (embedding_rebuild_run_id);

CREATE INDEX IF NOT EXISTS idx_embedding_rebuild_run_status
    ON embedding_rebuild_run (status);
