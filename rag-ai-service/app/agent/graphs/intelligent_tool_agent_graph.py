from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph

from app.agent.graphs.state import AgentGraphState
from app.agent.nodes.tool_agent_nodes import (
    agent_model_node,
    create_recommended_action_node,
    execute_readonly_tool_node,
    final_response_node,
)
from app.core.config import Settings, get_settings


def build_agent_graph(
    *,
    settings: Settings | None = None,
    chat_model: Any | None = None,
):
    """Build the single intelligent Agent graph; LangGraph owns the main path."""
    resolved_settings = settings or get_settings()
    graph = StateGraph(AgentGraphState)
    graph.add_node("agent_model", agent_model_node(settings=resolved_settings, chat_model=chat_model))
    graph.add_node("execute_readonly_tool", execute_readonly_tool_node)
    graph.add_node("create_recommended_action", create_recommended_action_node)
    graph.add_node("final_response", final_response_node)

    graph.add_edge(START, "agent_model")
    graph.add_conditional_edges(
        "agent_model",
        _route_after_model,
        {
            "execute_readonly_tool": "execute_readonly_tool",
            "create_recommended_action": "create_recommended_action",
            "final_response": "final_response",
            "__end__": END,
        },
    )
    graph.add_conditional_edges(
        "execute_readonly_tool",
        _route_after_tool,
        {
            "agent_model": "agent_model",
            "__end__": END,
        },
    )
    graph.add_edge("create_recommended_action", END)
    graph.add_edge("final_response", END)
    return graph.compile()


def _route_after_model(state: AgentGraphState) -> str:
    recorder = state["recorder"]
    if recorder.error_message:
        return "__end__"
    if state.get("pending_action_call"):
        return "create_recommended_action"
    if state.get("pending_tool_call"):
        return "execute_readonly_tool"
    return "final_response"


def _route_after_tool(state: AgentGraphState) -> str:
    if state["recorder"].error_message:
        return "__end__"
    return "agent_model"
