package com.example.rag.service.agent;

/**
 * MCP tools capability 下可被 Agent Runtime 直接调用的业务工具。
 */
public interface McpTool {
    /** MCP tool name。 */
    String name();

    /** 返回标准 MCP tool definition。 */
    McpToolDefinition definition();

    /** 执行只读低风险工具。 */
    McpToolResult call(McpToolContext context);
}
