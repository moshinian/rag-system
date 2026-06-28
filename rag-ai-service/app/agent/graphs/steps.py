from __future__ import annotations

import json
from typing import Any

from app.agent.events import emit_runtime_event
from app.agent.graphs.state import AgentGraphState
from app.agent.state import AgentStepResult


def execute_tool_node(
    state: AgentGraphState,
    node_name: str,
    tool_name: str,
    arguments: dict[str, Any] | None = None,
) -> AgentGraphState:
    """执行固定图中的工具节点，并统一写入 tool_results 和 step。"""
    if state.get("error_message"):
        # 前置节点失败时，固定图继续产出 SKIPPED step，便于 Java timeline 完整展示。
        return append_step(
            state,
            node_name,
            tool_name=tool_name,
            step_type="TOOL_CALL",
            status="SKIPPED",
            output={"reason": "previous step failed"},
        )

    emit_runtime_event(
        state,
        "TOOL_CALL_STARTED",
        tool_name=tool_name,
        status="RUNNING",
        message=f"{tool_name} 开始调用",
        payload={"arguments": arguments or {}},
    )
    execution = state["tool_client"].execute(tool_name, state["request"], arguments or {})
    # tool_results 是诊断节点读取工具原始输出的统一位置。
    tool_results = dict(state.get("tool_results", {}))
    tool_results[tool_name] = {
        "success": execution.success,
        "output": execution.output,
        "errorMessage": execution.error_message,
        "durationMs": execution.duration_ms,
    }

    next_state = dict(state)
    next_state["tool_results"] = tool_results
    if not execution.success:
        # 工具失败后不抛异常，而是把错误写进状态，让图进入失败报告路径。
        next_state["error_message"] = execution.error_message or f"{tool_name} failed"

    tool_event_type = "TOOL_CALL_COMPLETED" if execution.success else "TOOL_CALL_FAILED"
    emit_runtime_event(
        next_state,
        tool_event_type,
        tool_name=tool_name,
        status="SUCCEEDED" if execution.success else "FAILED",
        message=execution.error_message or f"{tool_name} 调用完成",
        payload={
            "success": execution.success,
            "durationMs": execution.duration_ms,
            "summary": summarize_observation(execution.output or {}),
            "errorMessage": execution.error_message,
        },
    )
    emit_runtime_event(
        next_state,
        "OBSERVATION_CREATED",
        tool_name=tool_name,
        status="SUCCEEDED" if execution.success else "FAILED",
        message=f"{tool_name} observation 已生成",
        payload={
            "success": execution.success,
            "summary": summarize_observation(execution.output or {}),
            "durationMs": execution.duration_ms,
            "errorMessage": execution.error_message,
        },
    )

    return append_step(
        next_state,
        node_name,
        tool_name=tool_name,
        step_type="TOOL_CALL",
        status="SUCCEEDED" if execution.success else "FAILED",
        output=execution.output,
        duration_ms=execution.duration_ms,
        error_message=execution.error_message,
    )


def append_step(
    state: AgentGraphState,
    node_name: str,
    *,
    tool_name: str | None = None,
    step_type: str = "NODE",
    status: str = "SUCCEEDED",
    input_json: str | None = None,
    output: dict[str, Any] | None = None,
    duration_ms: int | None = None,
    error_message: str | None = None,
) -> AgentGraphState:
    """追加一个 AgentStepResult，保持 Python 返回的 timeline 可审计。"""
    next_state = dict(state)
    steps = list(next_state.get("steps", []))
    # Step 只追加不原地覆盖，保证图状态在分支和测试中保持可追溯。
    steps.append(
        AgentStepResult(
            node_name=node_name,
            tool_name=tool_name,
            step_type=step_type,
            status=status,
            input_json=input_json,
            output_json=to_json(output or {}),
            duration_ms=duration_ms,
            error_message=error_message,
        )
    )
    next_state["steps"] = steps
    return next_state


def summarize_observation(output: dict[str, Any]) -> dict[str, Any]:
    """裁剪工具输出，只把适合回灌 planner 的摘要字段保留下来。"""
    summary: dict[str, Any] = {}
    # 白名单字段控制回灌模型的上下文体积，也避免把完整正文或敏感信息带入 prompt。
    for key in [
        "status",
        "kbCode",
        "questionAnsweringReady",
        "reembedRequired",
        "reembedInProgress",
        "nextStep",
        "statusCounts",
        "signals",
        "question",
        "topK",
        "defaultMode",
        "defaultTopK",
        "maxTopK",
        "denseCandidateLimit",
        "keywordCandidateLimit",
        "fusionK",
        "keywordStrategy",
        "keywordMinTokenLength",
        "keywordMinHitThreshold",
        "summary",
        "command",
        "mode",
    ]:
        if key in output:
            summary[key] = output[key]
    for key in ["dense", "hybrid"]:
        value = output.get(key)
        if isinstance(value, dict):
            summary[key] = {
                branch_key: value[branch_key]
                for branch_key in [
                    "retrievalMode",
                    "hitCount",
                    "denseHitCount",
                    "keywordHitCount",
                    "fusionStrategy",
                    "totalDurationMs",
                ]
                if branch_key in value
            }
    for key in ["failedTasks", "failedDocuments", "sources"]:
        value = output.get(key)
        if isinstance(value, list):
            # 列表只保留前三项，足够判断问题且不会随数据量线性放大 prompt。
            summary[key] = value[:3]
    return summary or {"keys": sorted(output.keys())[:10]}


def to_json(value: dict[str, Any]) -> str:
    """生成稳定紧凑的 JSON 字符串，便于测试和 Java 落库比较。"""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
