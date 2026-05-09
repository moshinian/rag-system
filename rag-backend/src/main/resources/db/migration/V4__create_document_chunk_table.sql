CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    document_id BIGINT NOT NULL REFERENCES document(id),
    chunk_index INT NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    title VARCHAR(255),
    content TEXT NOT NULL,
    content_length INT NOT NULL,
    token_count INT NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    metadata_json TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_chunk_document_index
    ON document_chunk (document_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_document_chunk_kb_document
    ON document_chunk (knowledge_base_id, document_id);

CREATE INDEX IF NOT EXISTS idx_document_chunk_document_status
    ON document_chunk (document_id, status);
