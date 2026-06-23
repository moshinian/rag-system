from __future__ import annotations

import warnings

from langchain_core._api.deprecation import LangChainPendingDeprecationWarning

warnings.filterwarnings(
    "ignore",
    message="The default value of `allowed_objects` will change in a future version.*",
    category=LangChainPendingDeprecationWarning,
    module="langgraph.cache.base",
)

from langgraph.graph import END, StateGraph

from app.agent.graphs.state import AgentGraphState
from app.agent.nodes.tool_agent_nodes import (
    create_recommended_action,
    execute_readonly_tool,
    fail_report,
    final_report,
    llm_plan,
    load_tools,
    route_after_decision,
    route_decision,
)


def build_intelligent_tool_agent_graph():
    """构建单 Agent Tool-use 循环图。"""
    workflow = StateGraph(AgentGraphState)
    # 图只负责编排节点，具体 planner / 工具 / 风险策略放在独立模块。
    workflow.add_node("load_tools", load_tools)
    workflow.add_node("llm_plan", llm_plan)
    workflow.add_node("route_decision", route_decision)
    workflow.add_node("execute_readonly_tool", execute_readonly_tool)
    workflow.add_node("create_recommended_action", create_recommended_action)
    workflow.add_node("final_report", final_report)
    workflow.add_node("fail_report", fail_report)

    workflow.set_entry_point("load_tools")
    # planner 决策后由 route_after_decision 分流到工具执行、待确认动作或最终报告。
    workflow.add_edge("load_tools", "llm_plan")
    workflow.add_edge("llm_plan", "route_decision")
    workflow.add_conditional_edges(
        "route_decision",
        route_after_decision,
        {
            "execute_readonly_tool": "execute_readonly_tool",
            "create_recommended_action": "create_recommended_action",
            "final_report": "final_report",
            "fail_report": "fail_report",
        },
    )
    workflow.add_edge("execute_readonly_tool", "llm_plan")
    workflow.add_edge("create_recommended_action", END)
    workflow.add_edge("final_report", END)
    workflow.add_edge("fail_report", END)
    return workflow.compile()
