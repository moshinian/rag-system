ALTER TABLE document
    ADD COLUMN IF NOT EXISTS storage_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS object_key VARCHAR(1024);

UPDATE document
SET storage_type = 'local'
WHERE storage_type IS NULL;

ALTER TABLE document
    ALTER COLUMN storage_type SET DEFAULT 'local',
    ALTER COLUMN storage_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_document_storage_object
    ON document(storage_type, object_key)
    WHERE object_key IS NOT NULL;
