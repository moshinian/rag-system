package com.example.rag.model.enums;

/**
 * Agent 推荐动作与确认执行状态。
 */
public enum AgentActionStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    REJECTED
}
