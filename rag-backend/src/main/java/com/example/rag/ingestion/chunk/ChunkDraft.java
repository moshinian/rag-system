package com.example.rag.ingestion.chunk;

/**
 * 切块阶段的中间对象。
 *
 * 该对象不是数据库实体，只表示一段待保存的 chunk 数据。
 */
public record ChunkDraft(
        int chunkIndex,
        String title,
        String content,
        int startOffset,
        int endOffset
) {
}
