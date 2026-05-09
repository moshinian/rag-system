CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS embedding_vector vector(512);

CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding_vector_cosine
    ON document_chunk
    USING ivfflat (embedding_vector vector_cosine_ops)
    WITH (lists = 100);
