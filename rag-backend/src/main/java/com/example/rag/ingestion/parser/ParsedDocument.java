package com.example.rag.ingestion.parser;

import java.util.List;

/**
 * 统一解析结果。
 *
 * 该对象不是数据库实体，而是解析和切块之间的中间模型。
 */
public record ParsedDocument(
        String parserName,
        String title,
        List<ParsedSection> sections
) {
}
