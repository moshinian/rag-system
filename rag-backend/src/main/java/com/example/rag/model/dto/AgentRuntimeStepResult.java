package com.example.rag.model.dto;

import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;

/**
 * Python Agent Runtime 返回的单个节点或工具调用结果。
 */
public record AgentRuntimeStepResult(
        String nodeName,
        String toolName,
        AgentStepType stepType,
        AgentStepStatus status,
        String inputJson,
        String outputJson,
        Long durationMs,
        String errorMessage
) {
}
