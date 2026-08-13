-- V4 与 V11 已分别建立等价的业务唯一索引。V27 在强化分布式幂等时
-- 使用了新名称，PostgreSQL 因而创建了两组重复索引。保留较早的稳定名称，
-- 删除重复副本，避免每次写入承担两次相同索引维护成本。
DROP INDEX IF EXISTS uq_indexing_task_active_document;
DROP INDEX IF EXISTS uq_document_chunk_document_index;
