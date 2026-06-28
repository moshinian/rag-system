package com.example.rag.model.enums;

/**
 * Java 规范化后落库并推送给前端的 Agent 事件类型。
 */
public enum AgentRunEventType {
    RUN_STARTED,
    STEP_STARTED,
    STEP_COMPLETED,
    STEP_FAILED,
    PLANNER_DECISION,
    TOOL_CALL_STARTED,
    TOOL_CALL_COMPLETED,
    TOOL_CALL_FAILED,
    OBSERVATION_CREATED,
    ACTION_RECOMMENDED,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_WAITING_CONFIRMATION;

    /** 判断当前事件是否表示 Java 业务终态。 */
    public boolean isTerminal() {
        return this == RUN_COMPLETED
                || this == RUN_FAILED
                || this == RUN_WAITING_CONFIRMATION;
    }
}
