UPDATE indexing_task AS parent
SET error_message = CASE child.trigger_source
        WHEN 'RECOVERY' THEN LEFT('Recovery task ' || child.id || ' failed: ' || COALESCE(child.error_message, 'Unknown indexing error'), 1024)
        ELSE LEFT('Retry task ' || child.id || ' failed: ' || COALESCE(child.error_message, 'Unknown indexing error'), 1024)
    END,
    recovered_at = NULL,
    updated_at = NOW()
FROM indexing_task AS child
WHERE child.parent_task_id = parent.id
  AND parent.task_type = 'DOCUMENT_INDEXING'
  AND child.task_type = 'DOCUMENT_INDEXING'
  AND child.status = 'FAILED'
  AND (
        parent.error_message = 'Manually retried by task ' || child.id
        OR parent.error_message = 'Recovered by task ' || child.id
      );
