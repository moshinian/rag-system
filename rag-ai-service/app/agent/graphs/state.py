from __future__ import annotations

from typing import Any, TypedDict

from app.agent.state import (
    AgentActionDraft,
    AgentDecision,
    AgentObservation,
    AgentRuntimeRequest,
    AgentStepResult,
    AgentToolDefinition,
)
from app.agent.tools import AgentToolClient


class AgentGraphState(TypedDict, total=False):
    """LangGraph 内部状态。

    这里使用 TypedDict 是为了贴合 LangGraph 的 state schema；对外协议仍由 state.py
    中的 Pydantic 模型负责。
    """

    request: AgentRuntimeRequest
    tool_client: AgentToolClient
    # LangGraph 会运行时解析 type hints，这里用 Any 避免 planner 协议产生循环导入。
    decision_client: Any
    tools: list[AgentToolDefinition]
    messages: list[dict[str, Any]]
    decision: AgentDecision | None
    observations: list[AgentObservation]
    tool_call_count: int
    tool_results: dict[str, Any]
    steps: list[AgentStepResult]
    recommended_actions: list[AgentActionDraft]
    diagnosis: dict[str, Any]
    summary: str | None
    error_message: str | None
    planner_error_message: str | None
    # streaming 专用对象不参与 JSON 协议，仅在线程内传递事件和取消状态。
    event_sink: Any
    event_sequence: Any
    current_node_invocation_id: str | None
    current_node_name: str | None
