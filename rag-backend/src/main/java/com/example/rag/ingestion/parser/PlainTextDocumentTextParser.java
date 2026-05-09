package com.example.rag.ingestion.parser;

import com.example.rag.persistence.entity.DocumentEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 纯文本解析器。
 *
 * 当前实现直接读取全文，并将其视为一个 section。
 */
@Component
public class PlainTextDocumentTextParser implements DocumentTextParser {

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(DocumentEntity document, Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .trim();
        // txt 没有天然标题层级，直接回退到文档展示名。
        String title = document.getDisplayName() == null || document.getDisplayName().isBlank()
                ? document.getFileName()
                : document.getDisplayName();
        if (content.isEmpty()) {
            return new ParsedDocument("plain-text", title, List.of());
        }
        return new ParsedDocument("plain-text", title, List.of(new ParsedSection(title, content)));
    }
}
