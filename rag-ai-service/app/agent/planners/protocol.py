from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from app.agent.graphs.state import AgentGraphState


class AgentDecisionClient(Protocol):
    def decide(self, state: "AgentGraphState") -> str:
        """Return a strict JSON AgentDecision string."""
