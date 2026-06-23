from __future__ import annotations

from typing import Any

from app.agent.graphs.state import AgentGraphState


def tool_output(state: AgentGraphState, tool_name: str) -> dict[str, Any]:
    """从 graph state 中安全取出某个工具的原始 output。"""
    result = state.get("tool_results", {}).get(tool_name, {})
    output = result.get("output")
    return output if isinstance(output, dict) else {}


def failed_tasks(indexing_tasks: dict[str, Any]) -> list[dict[str, Any]]:
    """提取 FAILED indexing task 列表，字段缺失时返回空列表。"""
    failed_tasks_value = indexing_tasks.get("failedTasks", [])
    return failed_tasks_value if isinstance(failed_tasks_value, list) else []


def retrieve_signals(retrieve_probe: dict[str, Any]) -> dict[str, Any]:
    """提取检索探测信号，避免诊断节点直接依赖复杂嵌套结构。"""
    signals = retrieve_probe.get("signals", {})
    return signals if isinstance(signals, dict) else {}
