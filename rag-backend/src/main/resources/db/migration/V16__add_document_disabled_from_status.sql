ALTER TABLE document
ADD COLUMN IF NOT EXISTS disabled_from_status VARCHAR(32);
