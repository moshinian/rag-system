ALTER TABLE document
    ADD COLUMN IF NOT EXISTS media_type VARCHAR(128);

UPDATE document
SET media_type = CASE lower(file_type)
    WHEN 'md' THEN 'text/markdown'
    WHEN 'txt' THEN 'text/plain'
    WHEN 'pdf' THEN 'application/pdf'
    ELSE 'application/octet-stream'
END
WHERE media_type IS NULL;

ALTER TABLE document
    ALTER COLUMN media_type SET NOT NULL;
