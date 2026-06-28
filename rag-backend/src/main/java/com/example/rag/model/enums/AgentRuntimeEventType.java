package com.example.rag.model.enums;

/**
 * Python Runtime 发给 Java 的内部事件类型。
 *
 * <p>Round 1 仅建立协议边界，真正的 Python SSE 消费在后续轮次接入。</p>
 */
public enum AgentRuntimeEventType {
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
    RUN_FAILED
}
