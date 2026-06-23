from __future__ import annotations

from app.agent.graphs.intelligent_tool_agent_graph import build_intelligent_tool_agent_graph
from app.agent.graphs.readiness_graph import build_readiness_diagnosis_graph
from app.agent.graphs.state import AgentGraphState
from app.agent.planners.protocol import AgentDecisionClient


def build_agent_graph():
    """兼容旧调用方的默认图入口，目前默认返回 readiness 固定诊断图。"""
    return build_readiness_diagnosis_graph()


__all__ = [
    "AgentGraphState",
    "AgentDecisionClient",
    "build_agent_graph",
    "build_readiness_diagnosis_graph",
    "build_intelligent_tool_agent_graph",
]
