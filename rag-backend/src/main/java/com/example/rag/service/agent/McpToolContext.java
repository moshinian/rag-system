package com.example.rag.service.agent;

import java.util.Map;

/**
 * MCP tools/call 传入业务工具的受控上下文。
 */
public record McpToolContext(
        Map<String, Object> arguments,
        Map<String, Object> meta
) {
    /** 使用知识库编码创建最小工具上下文。 */
    public static McpToolContext forKnowledgeBase(String kbCode) {
        return new McpToolContext(Map.of("kbCode", kbCode), Map.of());
    }

    /** 从 arguments 中读取 kbCode。 */
    public String kbCode() {
        Object value = arguments.get("kbCode");
        return value instanceof String text ? trimToNull(text) : null;
    }

    /** 从 arguments 中读取 question。 */
    public String question() {
        Object value = arguments.get("question");
        return value instanceof String text ? trimToNull(text) : null;
    }

    /** 从 arguments 中读取 attributes。 */
    public Map<String, Object> attributes() {
        Object value = arguments.get("attributes");
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String textKey) {
                attributes.put(textKey, item);
            }
        });
        return java.util.Collections.unmodifiableMap(attributes);
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }
}
