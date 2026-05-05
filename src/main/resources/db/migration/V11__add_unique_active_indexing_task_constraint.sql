CREATE UNIQUE INDEX IF NOT EXISTS uk_indexing_task_active_document_type
    ON indexing_task (document_id, task_type)
    WHERE status IN ('QUEUED', 'RUNNING');
