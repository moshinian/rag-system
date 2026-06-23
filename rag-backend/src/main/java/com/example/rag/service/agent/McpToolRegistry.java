package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);
    private final Map<String, McpTool> tools;
    private final Map<String, McpToolDefinition> definitions;

    /** 构造McpToolRegistry。 */
    public McpToolRegistry(List<McpTool> registeredTools) {
        Map<String, McpTool> toolMap = new LinkedHashMap<>();
        Map<String, McpToolDefinition> definitionMap = new LinkedHashMap<>();
        for (McpTool tool : registeredTools) {
            McpToolDefinition definition;
            try {
                definition = tool.definition();
            } catch (BusinessException exception) {
                log.error("Invalid MCP tool contract for {}: {}", tool.name(), exception.getMessage());
                throw exception;
            }
            if (definition.executionMode() != AgentToolExecutionMode.READ_ONLY
                    || definition.maxRiskLevel() != AgentActionRiskLevel.LOW
                    || definition.requiresConfirmation()) {
                throw new BusinessException("MCP tools/call only accepts READ_ONLY LOW tools: " + tool.name());
            }
            McpTool existing = toolMap.putIfAbsent(tool.name(), tool);
            if (existing != null) {
                throw new BusinessException("Duplicate MCP tool registered: " + tool.name());
            }
            definitionMap.put(tool.name(), definition);
        }
        this.tools = Map.copyOf(toolMap);
        this.definitions = Map.copyOf(definitionMap);
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
        return definitions.values();
    }

    /** 按工具名读取已校验 definition。 */
    public McpToolDefinition requireDefinition(String toolName) {
        McpToolDefinition definition = definitions.get(toolName);
        if (definition == null) {
            throw new BusinessException("MCP tool not found: " + toolName);
        }
        return definition;
    }
}
