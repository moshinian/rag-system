CREATE INDEX IF NOT EXISTS idx_document_chunk_search_vector
    ON document_chunk
    USING GIN (to_tsvector('simple', COALESCE(title, '') || ' ' || content));
