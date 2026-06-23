package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tools/list 和 tools/call 使用的只读低风险工具注册表。
 */
@Component
public class McpToolRegistry {
    private final Map<String, McpTool> tools;

    /** 构造McpToolRegistry。 */
    public McpToolRegistry(List<McpTool> registeredTools) {
        Map<String, McpTool> toolMap = new LinkedHashMap<>();
        for (McpTool tool : registeredTools) {
            McpToolDefinition definition = tool.definition();
            if (definition.executionMode() != AgentToolExecutionMode.READ_ONLY
                    || definition.maxRiskLevel() != AgentActionRiskLevel.LOW
                    || definition.requiresConfirmation()) {
                throw new BusinessException("MCP tools/call only accepts READ_ONLY LOW tools: " + tool.name());
            }
            McpTool existing = toolMap.putIfAbsent(tool.name(), tool);
            if (existing != null) {
                throw new BusinessException("Duplicate MCP tool registered: " + tool.name());
            }
        }
        this.tools = Map.copyOf(toolMap);
    }

    /** 按工具名查询工具。 */
    public Optional<McpTool> find(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /** 按工具名读取工具，不存在时抛出业务异常。 */
    public McpTool require(String toolName) {
        return find(toolName)
                .orElseThrow(() -> new BusinessException("MCP tool not found: " + toolName));
    }

    /** 返回所有 MCP tool definition。 */
    public Collection<McpToolDefinition> definitions() {
        return tools.values().stream()
                .map(McpTool::definition)
                .toList();
    }
}
