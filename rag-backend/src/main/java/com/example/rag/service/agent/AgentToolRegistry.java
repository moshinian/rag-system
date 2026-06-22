package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.dto.AgentToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 工具白名单注册表。
 */
@Component
public class AgentToolRegistry {
    private final Map<String, AgentTool> tools;

    /** 构造AgentToolRegistry。 */
    public AgentToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> toolMap = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            AgentTool existing = toolMap.putIfAbsent(tool.toolName(), tool);
            if (existing != null) {
                throw new BusinessException("Duplicate agent tool registered: " + tool.toolName());
            }
        }
        this.tools = Map.copyOf(toolMap);
    }

    /** 按工具名查询工具。 */
    public Optional<AgentTool> find(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /** 按工具名读取工具，不存在时抛出业务异常。 */
    public AgentTool require(String toolName) {
        return find(toolName)
                .orElseThrow(() -> new BusinessException("Agent tool not found: " + toolName));
    }

    /** 返回所有工具定义。 */
    public Collection<AgentToolDefinition> definitions() {
        return tools.values().stream()
                .map(AgentTool::definition)
                .toList();
    }
}
