package com.example.rag.common.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简单结构化日志消息构造器。
 */
public final class StructuredLogMessage {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    private StructuredLogMessage(String event) {
        field("event", event);
    }

    public static StructuredLogMessage of(String event) {
        return new StructuredLogMessage(event);
    }

    public StructuredLogMessage field(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        fields.put(key, value);
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                builder.append(' ');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(quoteIfNeeded(entry.getValue()));
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return build();
    }

    private String quoteIfNeeded(Object value) {
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return "\"\"";
        }
        boolean needsQuote = text.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '=' || ch == '"');
        if (!needsQuote) {
            return text;
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
