package com.example.rag.service.agent;

import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.service.SystemHealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Agent 只读系统健康检查工具。
 */
@Component
public class SystemHealthAgentTool implements AgentTool {
    public static final String TOOL_NAME = "system.health.check";
    private final SystemHealthService systemHealthService;
    private final ObjectMapper objectMapper;

    /** 构造SystemHealthAgentTool。 */
    public SystemHealthAgentTool(SystemHealthService systemHealthService, ObjectMapper objectMapper) {
        this.systemHealthService = systemHealthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                AgentToolExecutionMode.READ_ONLY,
                AgentActionRiskLevel.LOW
        );
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        long startedAt = System.nanoTime();
        String outputJson = AgentToolSupport.toJson(objectMapper, systemHealthService.currentStatus());
        return AgentToolResult.success(TOOL_NAME, outputJson, AgentToolSupport.elapsedMillis(startedAt));
    }
}
