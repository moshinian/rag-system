package com.example.rag.ingestion.parser;

/**
 * 解析后按章节或段落拆出的文本片段。
 */
public record ParsedSection(
        String title,
        String content
) {
}
