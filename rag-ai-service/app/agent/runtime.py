from __future__ import annotations

from functools import lru_cache
from queue import Empty, Queue
from threading import Event, Thread
from typing import Any, Iterator

from app.agent.events import (
    AgentRunCancelled,
    QueueAgentEventSink,
    RuntimeEventSequence,
    emit_runtime_event,
    format_sse,
    heartbeat_sse,
)
from app.agent.graphs.intelligent_tool_agent_graph import build_intelligent_tool_agent_graph
from app.agent.graphs.readiness_graph import build_readiness_diagnosis_graph
from app.agent.planners.llm import LlmAgentDecisionClient
from app.agent.planners.protocol import AgentDecisionClient
from app.agent.state import (
    AgentRuntimeEvent,
    AgentRuntimeEventType,
    AgentRuntimeRequest,
    AgentRuntimeResponse,
)
from app.agent.tools import AgentToolClient, McpAgentToolClient
from app.core.config import get_settings

HEARTBEAT_INTERVAL_SECONDS = 10.0


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
            final_state = self._invoke_graph(request)
        except Exception as exc:  # pragma: no cover - defensive guard
            # Runtime 作为 Java 调用边界，兜底把未预期异常转换成协议内 FAILED。
            return AgentRuntimeResponse(
                status="FAILED",
                summary="Agent Runtime 执行失败。",
                steps=[],
                recommended_actions=[],
                error_message=str(exc),
            )

        return self._to_response(final_state)

    def stream_sse(self, request: AgentRuntimeRequest) -> Iterator[str]:
        """执行 LangGraph，并按 step 粒度向 Java 输出 SSE。"""
        event_queue: Queue[AgentRuntimeEvent | None] = Queue()
        cancellation = Event()
        sink = QueueAgentEventSink(event_queue, cancellation)
        sequence = RuntimeEventSequence(request.run_code)

        def run_graph() -> None:
            # terminal 标记只在当前 worker 线程内修改，保证恰好发送一次。
            terminal_emitted = False

            def emit_terminal_once(
                event_type: AgentRuntimeEventType,
                *,
                status: str,
                message: str,
                payload: dict[str, Any],
            ) -> None:
                nonlocal terminal_emitted
                if terminal_emitted or cancellation.is_set():
                    return
                terminal_emitted = emit_runtime_event(
                    stream_state,
                    event_type,
                    status=status,
                    message=message,
                    payload=payload,
                    terminal=True,
                )

            stream_state = self._initial_state(
                request,
                event_sink=sink,
                event_sequence=sequence,
            )
            try:
                emit_runtime_event(
                    stream_state,
                    "RUN_STARTED",
                    status="RUNNING",
                    message="Agent Runtime 开始执行",
                    payload={"runMode": request.run_mode},
                )
                final_state = self._invoke_graph(request, initial_state=stream_state)
                if cancellation.is_set():
                    return
                error_message = final_state.get("error_message")
                if error_message:
                    emit_terminal_once(
                        "RUN_FAILED",
                        status="FAILED",
                        message=str(error_message),
                        payload={
                            "summary": final_state.get("summary"),
                            "errorMessage": error_message,
                        },
                    )
                else:
                    emit_terminal_once(
                        "RUN_COMPLETED",
                        status="SUCCEEDED",
                        message=final_state.get("summary") or "Agent Runtime 执行完成",
                        payload={
                            "summary": final_state.get("summary"),
                            "recommendedActions": [
                                action.model_dump(by_alias=True)
                                for action in final_state.get("recommended_actions", [])
                            ],
                        },
                    )
            except AgentRunCancelled:
                # 客户端已断开，不再向已关闭的 stream 尝试发送 terminal。
                return
            except Exception as exc:
                emit_terminal_once(
                    "RUN_FAILED",
                    status="FAILED",
                    message=str(exc),
                    payload={"errorMessage": str(exc)},
                )
            finally:
                if not cancellation.is_set() and not terminal_emitted:
                    emit_terminal_once(
                        "RUN_FAILED",
                        status="FAILED",
                        message="Agent Runtime ended without terminal event",
                        payload={"errorMessage": "Agent Runtime ended without terminal event"},
                    )
                # None 由 worker 直接入队，不经过已取消后拒绝 emit 的 sink。
                event_queue.put(None)

        worker = Thread(
            target=run_graph,
            name=f"agent-stream-{request.run_code}",
            daemon=True,
        )
        worker.start()

        try:
            while True:
                try:
                    event = event_queue.get(timeout=HEARTBEAT_INTERVAL_SECONDS)
                except Empty:
                    if cancellation.is_set():
                        break
                    yield heartbeat_sse()
                    continue
                if event is None:
                    break
                yield format_sse(event)
        finally:
            # StreamingResponse/generator 被关闭时通知 sink 和后续 node 尽快停止。
            cancellation.set()

    def _invoke_graph(
        self,
        request: AgentRuntimeRequest,
        *,
        initial_state: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """选择并执行对应 LangGraph。"""
        graph = (
            self._intelligent_graph
            if request.run_mode == "INTELLIGENT_TOOL_AGENT"
            else self._readiness_graph
        )
        return graph.invoke(initial_state or self._initial_state(request))

    def _initial_state(
        self,
        request: AgentRuntimeRequest,
        *,
        event_sink: QueueAgentEventSink | None = None,
        event_sequence: RuntimeEventSequence | None = None,
    ) -> dict[str, Any]:
        """构造一次 graph invocation 的初始状态。"""
        state: dict[str, Any] = {
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
        if event_sink is not None and event_sequence is not None:
            state["event_sink"] = event_sink
            state["event_sequence"] = event_sequence
        return state

    def _to_response(self, final_state: dict[str, Any]) -> AgentRuntimeResponse:
        """把最终 graph state 转换成旧 JSON 协议响应。"""
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
