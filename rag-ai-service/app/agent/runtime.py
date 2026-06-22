from __future__ import annotations

from functools import lru_cache

from app.agent.graph import (
    AgentDecisionClient,
    HeuristicAgentDecisionClient,
    build_intelligent_tool_agent_graph,
    build_readiness_diagnosis_graph,
)
from app.agent.state import AgentRuntimeRequest, AgentRuntimeResponse
from app.agent.tools import AgentToolClient, JavaAgentToolClient, StaticAgentToolClient
from app.core.config import get_settings


class AgentRuntime:
    def __init__(
        self,
        tool_client: AgentToolClient | None = None,
        decision_client: AgentDecisionClient | None = None,
    ) -> None:
        self._tool_client = tool_client or _default_tool_client()
        self._decision_client = decision_client or HeuristicAgentDecisionClient()
        self._readiness_graph = build_readiness_diagnosis_graph()
        self._intelligent_graph = build_intelligent_tool_agent_graph()

    def run(self, request: AgentRuntimeRequest) -> AgentRuntimeResponse:
        try:
            graph = self._intelligent_graph if request.run_mode == "INTELLIGENT_TOOL_AGENT" else self._readiness_graph
            final_state = graph.invoke(
                {
                    "request": request,
                    "tool_client": self._tool_client,
                    "decision_client": self._decision_client,
                    "tools": [],
                    "messages": [],
                    "decision": None,
                    "observations": [],
                    "tool_call_count": 0,
                    "tool_results": {},
                    "steps": [],
                    "recommended_actions": [],
                    "summary": None,
                    "error_message": None,
                }
            )
        except Exception as exc:  # pragma: no cover - defensive guard
            return AgentRuntimeResponse(
                status="FAILED",
                summary="Agent Runtime 执行失败。",
                steps=[],
                recommended_actions=[],
                error_message=str(exc),
            )

        error_message = final_state.get("error_message")
        return AgentRuntimeResponse(
            status="FAILED" if error_message else "SUCCEEDED",
            summary=final_state.get("summary"),
            steps=final_state.get("steps", []),
            recommended_actions=final_state.get("recommended_actions", []),
            error_message=error_message,
        )


@lru_cache
def get_agent_runtime() -> AgentRuntime:
    return AgentRuntime()


def _resolve_tool_client_name(value: str | None) -> str:
    return (value or "static").strip().lower()


def _default_tool_client() -> AgentToolClient:
    settings = get_settings()
    client_name = _resolve_tool_client_name(settings.agent_tool_client)
    if client_name == "java":
        return JavaAgentToolClient(settings)
    return StaticAgentToolClient(settings)
