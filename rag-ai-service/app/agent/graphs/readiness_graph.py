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
from app.agent.nodes.readiness_nodes import (
    diagnose,
    documents_status_scan,
    generate_report,
    indexing_tasks_scan,
    kb_readiness_check,
    parse_goal,
    qa_retrieve_probe,
    recommend_actions,
    system_health_check,
)


def build_readiness_diagnosis_graph():
    """构建确定性 readiness 诊断图。"""
    workflow = StateGraph(AgentGraphState)
    # 节点名会进入 Java agent_step.node_name，重构时必须保持稳定。
    workflow.add_node("parse_goal", parse_goal)
    workflow.add_node("system_health_check", system_health_check)
    workflow.add_node("kb_readiness_check", kb_readiness_check)
    workflow.add_node("documents_status_scan", documents_status_scan)
    workflow.add_node("indexing_tasks_scan", indexing_tasks_scan)
    workflow.add_node("qa_retrieve_probe", qa_retrieve_probe)
    workflow.add_node("diagnose", diagnose)
    workflow.add_node("recommend_actions", recommend_actions)
    workflow.add_node("generate_report", generate_report)

    workflow.set_entry_point("parse_goal")
    # 固定图按业务诊断顺序串行执行，便于演示和问题复盘。
    workflow.add_edge("parse_goal", "system_health_check")
    workflow.add_edge("system_health_check", "kb_readiness_check")
    workflow.add_edge("kb_readiness_check", "documents_status_scan")
    workflow.add_edge("documents_status_scan", "indexing_tasks_scan")
    workflow.add_edge("indexing_tasks_scan", "qa_retrieve_probe")
    workflow.add_edge("qa_retrieve_probe", "diagnose")
    workflow.add_edge("diagnose", "recommend_actions")
    workflow.add_edge("recommend_actions", "generate_report")
    workflow.add_edge("generate_report", END)
    return workflow.compile()
