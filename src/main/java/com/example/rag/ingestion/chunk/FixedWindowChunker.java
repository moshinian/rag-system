package com.example.rag.ingestion.chunk;

import com.example.rag.ingestion.parser.ParsedDocument;
import com.example.rag.ingestion.parser.ParsedSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定窗口切块器。
 *
 * 当前实现以稳定性和可解释性为优先，便于后续继续调参。
 */
@Component
public class FixedWindowChunker {

    private static final int MAX_CHUNK_CHARS = 600;
    private static final int OVERLAP_CHARS = 80;
    private static final int MIN_BREAK_SEARCH_OFFSET = 240;

    /**
     * 把解析结果切成多个 chunk。
     */
    public List<ChunkDraft> chunk(ParsedDocument parsedDocument) {
        List<ChunkDraft> drafts = new ArrayList<>();
        int chunkIndex = 0;
        int baseOffset = 0;

        for (ParsedSection section : parsedDocument.sections()) {
            String content = normalize(section.content());
            if (content.isEmpty()) {
                continue;
            }

            int cursor = 0;
            while (cursor < content.length()) {
                int start = skipLeadingWhitespace(content, cursor);
                if (start >= content.length()) {
                    break;
                }

                int end = Math.min(start + MAX_CHUNK_CHARS, content.length());
                if (end < content.length()) {
                    // 优先把 chunk 截在更自然的边界，例如句号或换行。
                    int breakPoint = findBreakPoint(content, start, end);
                    if (breakPoint > start + MIN_BREAK_SEARCH_OFFSET) {
                        end = breakPoint;
                    }
                }

                int trimmedEnd = trimTrailingWhitespace(content, end);
                if (trimmedEnd <= start) {
                    cursor = end;
                    continue;
                }

                drafts.add(new ChunkDraft(
                        chunkIndex++,
                        section.title(),
                        content.substring(start, trimmedEnd),
                        baseOffset + start,
                        baseOffset + trimmedEnd
                ));

                if (trimmedEnd >= content.length()) {
                    break;
                }

                // overlap 用来减少边界截断带来的上下文损失。
                cursor = Math.max(trimmedEnd - OVERLAP_CHARS, start + 1);
            }

            // section 之间粗略增加偏移量，形成全局位置感。
            baseOffset += content.length() + 2;
        }

        return drafts;
    }

    /**
     * 统一去掉头尾空白。
     */
    private String normalize(String content) {
        return content == null ? "" : content.trim();
    }

    /**
     * 跳过 chunk 开头的空白字符。
     */
    private int skipLeadingWhitespace(String content, int cursor) {
        int index = cursor;
        while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * 去掉 chunk 结尾的空白字符。
     */
    private int trimTrailingWhitespace(String content, int cursor) {
        int index = cursor;
        while (index > 0 && Character.isWhitespace(content.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    /**
     * 尽量寻找更自然的截断位置。
     */
    private int findBreakPoint(String content, int start, int end) {
        for (int index = end; index > start; index--) {
            char ch = content.charAt(index - 1);
            if (ch == '\n' || ch == '.' || ch == '!' || ch == '?' || ch == ';') {
                return index;
            }
        }
        for (int index = end; index > start; index--) {
            if (Character.isWhitespace(content.charAt(index - 1))) {
                return index;
            }
        }
        return end;
    }
}
