ALTER TABLE tryon_task
    ADD COLUMN effective_provider VARCHAR(30) NULL AFTER provider,
    ADD COLUMN fallback_used BIT NOT NULL DEFAULT 0 AFTER effective_provider,
    ADD COLUMN fallback_reason VARCHAR(500) NULL AFTER fallback_used,
    ADD COLUMN result_kind VARCHAR(30) NOT NULL DEFAULT 'VIRTUAL_TRY_ON' AFTER fallback_reason;

ALTER TABLE tryon_task_item
    ADD COLUMN effective_provider VARCHAR(30) NULL AFTER stage_status,
    ADD COLUMN fallback_used BIT NOT NULL DEFAULT 0 AFTER effective_provider,
    ADD COLUMN fallback_reason VARCHAR(500) NULL AFTER fallback_used,
    ADD COLUMN result_kind VARCHAR(30) NULL AFTER fallback_reason;

UPDATE tryon_task
SET effective_provider = provider,
    result_kind = CASE WHEN provider = 'MOCK' THEN 'MOCK_PREVIEW' ELSE 'VIRTUAL_TRY_ON' END
WHERE effective_provider IS NULL;

UPDATE tryon_task_item item
JOIN tryon_task task ON task.id = item.task_id
SET item.effective_provider = task.provider,
    item.result_kind = CASE WHEN task.provider = 'MOCK' THEN 'MOCK_PREVIEW' ELSE 'VIRTUAL_TRY_ON' END
WHERE item.selected = 1 AND item.effective_provider IS NULL;
