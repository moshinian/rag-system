from __future__ import annotations

from functools import lru_cache

from app.agent.graphs.intelligent_tool_agent_graph import build_intelligent_tool_agent_graph
from app.agent.graphs.readiness_graph import build_readiness_diagnosis_graph
from app.agent.planners.llm import LlmAgentDecisionClient
from app.agent.planners.protocol import AgentDecisionClient
from app.agent.state import AgentRuntimeRequest, AgentRuntimeResponse
from app.agent.tools import AgentToolClient, McpAgentToolClient
from app.core.config import get_settings


class AgentRuntime:
    """Agent Runtime 入口，负责选择图、注入工具客户端和 planner。"""

    def __init__(
        self,
        tool_client: AgentToolClient | None = None,
        decision_client: AgentDecisionClient | None = None,
    ) -> None:
        """创建 Runtime，并预编译两条 LangGraph 图。"""
        # tool_client / decision_client 可注入，便于测试用 fake/mock 隔离外部依赖。
        self._tool_client = tool_client or _default_tool_client()
        self._decision_client = decision_client or _default_decision_client()
        # 图结构是稳定的，初始化时编译一次，避免每个 run 重复构图。
        self._readiness_graph = build_readiness_diagnosis_graph()
        self._intelligent_graph = build_intelligent_tool_agent_graph()

    def run(self, request: AgentRuntimeRequest) -> AgentRuntimeResponse:
        """执行一次 Agent run，并把 LangGraph state 收口为 Java 侧协议响应。"""
        try:
            # runMode 决定走固定诊断图还是智能 Tool-use 图。
            graph = self._intelligent_graph if request.run_mode == "INTELLIGENT_TOOL_AGENT" else self._readiness_graph
            final_state = graph.invoke(
                {
                    # 下面字段是 LangGraph 内部状态初始值；Java 仍然是 run/action 编号权威。
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
                    "planner_error_message": None,
                }
            )
        except Exception as exc:  # pragma: no cover - defensive guard
            # Runtime 作为 Java 调用边界，兜底把未预期异常转换成协议内 FAILED。
            return AgentRuntimeResponse(
                status="FAILED",
                summary="Agent Runtime 执行失败。",
                steps=[],
                recommended_actions=[],
                error_message=str(exc),
            )

        error_message = final_state.get("error_message")
        # Python 只返回草案结果；WAITING_CONFIRMATION 等 run 状态由 Java 根据 actions 决定。
        return AgentRuntimeResponse(
            status="FAILED" if error_message else "SUCCEEDED",
            summary=final_state.get("summary"),
            steps=final_state.get("steps", []),
            recommended_actions=final_state.get("recommended_actions", []),
            error_message=error_message,
        )


@lru_cache
def get_agent_runtime() -> AgentRuntime:
    """缓存 Runtime，复用已编译图和底层工具客户端。"""
    return AgentRuntime()


def _resolve_tool_client_name(value: str | None) -> str:
    """归一化工具客户端配置，空值默认使用 mcp。"""
    return (value or "mcp").strip().lower()


def _default_tool_client() -> AgentToolClient:
    """根据配置选择 MCP tools client。"""
    settings = get_settings()
    client_name = _resolve_tool_client_name(settings.agent_tool_client)
    if client_name == "mcp":
        return McpAgentToolClient(settings)
    raise ValueError(f"Unsupported agent_tool_client: {settings.agent_tool_client}")


def _default_decision_client() -> AgentDecisionClient:
    """创建唯一生产 planner：真实 LLM AgentDecision client。"""
    return LlmAgentDecisionClient(get_settings())
