from __future__ import annotations

from typing import Any, TypedDict

from langchain_core.messages import BaseMessage

from app.agent.recording import AgentRunRecorder
from app.agent.state import AgentRuntimeRequest
from app.agent.tools import AgentToolClient
from app.agent.tools.catalog import LangChainToolCatalog


class AgentGraphState(TypedDict, total=False):
    """由 LangGraph graph 持有的 Runtime 状态。"""

    request: AgentRuntimeRequest
    tool_client: AgentToolClient
    recorder: AgentRunRecorder
    catalog: LangChainToolCatalog
    messages: list[BaseMessage]
    pending_tool_calls: list[dict[str, Any]]
    pending_action_call: dict[str, Any] | None
    tool_call_count: int
