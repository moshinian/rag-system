package com.example.rag.service.agent;

import java.util.Map;

/**
 * MCP tools/call 传入业务工具的受控上下文。
 */
public record McpToolContext(
        String kbCode,
        String question,
        String runCode,
        String operator,
        Map<String, Object> attributes
) {
    /** 使用知识库编码创建最小工具上下文。 */
    public static McpToolContext forKnowledgeBase(String kbCode) {
        return new McpToolContext(kbCode, null, null, null, Map.of());
    }
}
