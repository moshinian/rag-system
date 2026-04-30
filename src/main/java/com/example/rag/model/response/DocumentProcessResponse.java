package com.example.rag.model.response;

import java.time.OffsetDateTime;

/**
 * 文档处理返回对象。
 *
 * 描述本次处理使用的解析器、生成的 chunk 数量以及最终状态。
 */
public record DocumentProcessResponse(
        Long documentId,
        String documentCode,
        String knowledgeBaseCode,
        String fileType,
        String status,
        Integer chunkCount,
        String parserName,
        OffsetDateTime updatedAt
) {
}
