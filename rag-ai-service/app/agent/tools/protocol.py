from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from app.agent.state import AgentRuntimeRequest, AgentToolDefinition


@dataclass(frozen=True)
class AgentToolExecution:
    """工具执行结果的统一封装，屏蔽 Java/MCP/CLI 等来源差异。"""

    tool_name: str
    success: bool
    output: dict[str, Any] | None = None
    error_message: str | None = None
    duration_ms: int = 0


class AgentToolClient(Protocol):
    """Agent 工具客户端协议，统一工具发现与执行入口。"""

    def definitions(self) -> list[AgentToolDefinition]:
        """返回当前 Runtime 可见的工具定义。"""

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict[str, Any] | None = None,
    ) -> AgentToolExecution:
        """执行一个工具，并返回标准化 observation。"""
