UPDATE indexing_task
SET task_stage = 'COMPLETED',
    updated_at = NOW()
WHERE task_type = 'DOCUMENT_PROCESS'
  AND status = 'SUCCEEDED'
  AND task_stage = 'QUEUED';
