from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from app.agent.graphs.state import AgentGraphState


class AgentDecisionClient(Protocol):
    """Planner 客户端协议，屏蔽真实 LLM 与测试替身的实现差异。"""

    def decide(self, state: "AgentGraphState") -> str:
        """返回严格符合 AgentDecision 的 JSON 字符串。"""
