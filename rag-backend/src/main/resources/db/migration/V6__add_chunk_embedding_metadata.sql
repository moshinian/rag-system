ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128),
    ADD COLUMN IF NOT EXISTS embedding_error_message VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS embedding_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_document_chunk_kb_embedding_status
    ON document_chunk (knowledge_base_id, embedding_status);
