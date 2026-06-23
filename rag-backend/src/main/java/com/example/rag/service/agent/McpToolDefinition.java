package com.example.rag.service.agent;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tools/list 返回的标准工具定义。
 */
public record McpToolDefinition(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations,
        AgentToolExecutionMode executionMode,
        AgentActionRiskLevel maxRiskLevel,
        boolean requiresConfirmation,
        Long timeoutMs
) {
    /** 构造显式声明 schema 的 MCP 工具定义。 */
    public static McpToolDefinition of(String name,
                                       String title,
                                       String description,
                                       Map<String, Object> inputSchema,
                                       Map<String, Object> outputSchema,
                                       AgentToolExecutionMode executionMode,
                                       AgentActionRiskLevel maxRiskLevel,
                                       boolean requiresConfirmation,
                                       Long timeoutMs) {
        McpToolContractValidator.validate(
                name,
                inputSchema,
                outputSchema == null ? Map.of() : outputSchema
        );
        return new McpToolDefinition(
                name,
                title,
                description,
                inputSchema,
                outputSchema == null ? Map.of() : outputSchema,
                defaultAnnotations(executionMode, maxRiskLevel, requiresConfirmation),
                executionMode,
                maxRiskLevel,
                requiresConfirmation,
                timeoutMs
        );
    }

    /** 转为 MCP 协议字段。 */
    public Map<String, Object> toProtocol() {
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("name", name);
        protocol.put("title", title);
        protocol.put("description", description);
        protocol.put("inputSchema", inputSchema);
        if (outputSchema != null && !outputSchema.isEmpty()) {
            protocol.put("outputSchema", outputSchema);
        }
        protocol.put("annotations", annotations);
        return protocol;
    }

    private static Map<String, Object> defaultAnnotations(AgentToolExecutionMode executionMode,
                                                          AgentActionRiskLevel maxRiskLevel,
                                                          boolean requiresConfirmation) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("x-rag.executionMode", executionMode.name());
        annotations.put("x-rag.maxRiskLevel", maxRiskLevel.name());
        annotations.put("x-rag.requiresConfirmation", requiresConfirmation);
        return annotations;
    }
}
