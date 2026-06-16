package com.example.rag.model.enums;

/**
 * Agent 节点或工具调用状态。
 */
public enum AgentStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
