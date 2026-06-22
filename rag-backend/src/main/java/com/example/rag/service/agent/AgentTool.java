package com.example.rag.service.agent;

import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;

/**
 * Agent 可调用工具。
 */
public interface AgentTool {
    /** 工具名称。 */
    String toolName();

    /** 工具白名单定义。 */
    AgentToolDefinition definition();

    /** 执行工具。 */
    AgentToolResult execute(AgentToolContext context);
}
