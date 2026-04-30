package com.example.rag.ingestion.parser;

import com.example.rag.model.entity.DocumentEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 文本解析器。
 *
 * 当前实现按标题拆分 section，并对少量 Markdown 语法做轻量清理。
 */
@Component
public class MarkdownDocumentTextParser implements DocumentTextParser {

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(DocumentEntity document, Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<ParsedSection> sections = new ArrayList<>();

        String defaultTitle = deriveDefaultTitle(document);
        String currentTitle = defaultTitle;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                // 空行保留成段落边界，便于后续自然切块。
                appendParagraphBreak(currentContent);
                continue;
            }

            if (trimmed.startsWith("```")) {
                // 当前版本先忽略围栏标记本身，不把它落入正文。
                continue;
            }

            if (trimmed.matches("^#{1,6}\\s+.*$")) {
                // 遇到标题时先冲刷上一段，再切换当前 section 标题。
                flushSection(sections, currentTitle, currentContent);
                currentTitle = trimmed.replaceFirst("^#{1,6}\\s+", "").trim();
                continue;
            }

            appendLine(currentContent, normalizeMarkdownLine(trimmed));
        }

        flushSection(sections, currentTitle, currentContent);
        return new ParsedDocument("markdown", defaultTitle, sections);
    }

    /** 用双换行模拟段落边界。 */
    private void appendParagraphBreak(StringBuilder builder) {
        if (builder.isEmpty()) {
            return;
        }
        if (!builder.toString().endsWith("\n\n")) {
            builder.append("\n\n");
        }
    }

    /** 追加正文行，尽量保持可读的换行结构。 */
    private void appendLine(StringBuilder builder, String line) {
        if (line.isBlank()) {
            return;
        }
        if (!builder.isEmpty() && !builder.toString().endsWith("\n") && !builder.toString().endsWith("\n\n")) {
            builder.append('\n');
        }
        builder.append(line);
    }

    /** 把当前 section 写入结果列表。 */
    private void flushSection(List<ParsedSection> sections, String title, StringBuilder content) {
        String normalized = content.toString().trim();
        if (!normalized.isEmpty()) {
            sections.add(new ParsedSection(title, normalized));
        }
        content.setLength(0);
    }

    /** 没有显式主标题时，退回到 displayName 或 fileName。 */
    private String deriveDefaultTitle(DocumentEntity document) {
        String displayName = document.getDisplayName();
        return displayName == null || displayName.isBlank() ? document.getFileName() : displayName;
    }

    /** 做轻量的 Markdown 语法清洗。 */
    private String normalizeMarkdownLine(String line) {
        String normalized = line
                .replaceFirst("^[-*+]\\s+", "")
                .replaceFirst("^\\d+\\.\\s+", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
        return normalized.trim();
    }
}
