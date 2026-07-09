from __future__ import annotations

from functools import lru_cache
from queue import Empty, Queue
from threading import Event, Thread
from typing import Any, Iterator

from app.agent.events import (
    AgentRunCancelled,
    LangGraphStreamEventSink,
    QueueAgentEventSink,
    RuntimeEventEmitter,
    RuntimeEventSequence,
    format_sse,
    heartbeat_sse,
)
from app.agent.graph import build_agent_graph
from app.agent.recording import AgentRunRecorder
from app.agent.state import (
    AgentRuntimeEvent,
    AgentRuntimeRequest,
    AgentRuntimeResponse,
)
from app.agent.tools import AgentToolClient, McpAgentToolClient
from app.agent.tools.catalog import LangChainToolCatalog
from app.core.config import get_settings

HEARTBEAT_INTERVAL_SECONDS = 10.0
STREAMING_MODE_RECORDER = "recorder"
STREAMING_MODE_LANGGRAPH = "langgraph"


class AgentRuntime:
    """Agent Runtime 入口；主执行路径由 LangGraph graph 承载。"""

    def __init__(
        self,
        tool_client: AgentToolClient | None = None,
        graph: Any | None = None,
        chat_model: Any | None = None,
        streaming_mode: str | None = None,
    ) -> None:
        """创建 Runtime，并注入 LangGraph graph。"""
        # tool_client / graph / chat_model 可注入，便于测试用 fake/mock 隔离外部依赖。
        self._tool_client = tool_client or _default_tool_client()
        self._graph = graph or build_agent_graph(chat_model=chat_model)
        self._streaming_mode = _resolve_streaming_mode(streaming_mode or get_settings().agent_streaming_mode)

    def run(self, request: AgentRuntimeRequest) -> AgentRuntimeResponse:
        """执行一次 Agent run，并把 LangGraph state 收口为 Java 侧协议响应。"""
        recorder = AgentRunRecorder(request=request)
        try:
            result = self._graph.invoke(self._initial_state(request, recorder))
            graph_recorder = result.get("recorder", recorder)
            return graph_recorder.to_response()
        except Exception as exc:  # pragma: no cover - defensive guard
            # Runtime 作为 Java 调用边界，兜底把未预期异常转换成协议内 FAILED。
            return AgentRuntimeResponse(
                status="FAILED",
                summary="Agent Runtime 执行失败。",
                steps=[],
                recommended_actions=[],
                error_message=str(exc),
            )

    def stream_sse(self, request: AgentRuntimeRequest) -> Iterator[str]:
        """执行 LangGraph graph，并按 step 粒度向 Java 输出 SSE。"""
        if self._streaming_mode == STREAMING_MODE_LANGGRAPH:
            yield from self._stream_sse_with_langgraph_stream(request)
            return
        yield from self._stream_sse_with_recorder_sink(request)

    def _stream_sse_with_recorder_sink(self, request: AgentRuntimeRequest) -> Iterator[str]:
        """使用现有 recorder sink 输出 SSE，作为默认稳定路径。"""
        event_queue: Queue[AgentRuntimeEvent | None] = Queue()
        cancellation = Event()
        sink = QueueAgentEventSink(event_queue, cancellation)
        sequence = RuntimeEventSequence(request.run_code)
        emitter = RuntimeEventEmitter(request, sink, sequence)

        def run_graph() -> None:
            # terminal 标记只在当前 worker 线程内修改，保证恰好发送一次。
            terminal_emitted = False

            def emit_terminal_once(
                event_type: str,
                *,
                status: str,
                message: str,
                payload: dict[str, Any],
            ) -> None:
                nonlocal terminal_emitted
                if terminal_emitted or cancellation.is_set():
                    return
                terminal_emitted = emitter.emit(
                    event_type,
                    status=status,
                    message=message,
                    payload=payload,
                    terminal=True,
                )

            try:
                emitter.emit(
                    "RUN_STARTED",
                    status="RUNNING",
                    message="Agent Runtime 开始执行",
                    payload={},
                )
                recorder = AgentRunRecorder(request=request, event_emitter=emitter)
                result = self._graph.invoke(self._initial_state(request, recorder))
                response = result.get("recorder", recorder).to_response()
                if cancellation.is_set():
                    return
                if response.error_message:
                    emit_terminal_once(
                        "RUN_FAILED",
                        status="FAILED",
                        message=str(response.error_message),
                        payload={
                            "summary": response.summary,
                            "errorMessage": response.error_message,
                        },
                    )
                else:
                    emit_terminal_once(
                        "RUN_COMPLETED",
                        status="SUCCEEDED",
                        message=response.summary or "Agent Runtime 执行完成",
                        payload={
                            "summary": response.summary,
                            "recommendedActions": [
                                action.model_dump(by_alias=True)
                                for action in response.recommended_actions
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

    def _stream_sse_with_langgraph_stream(self, request: AgentRuntimeRequest) -> Iterator[str]:
        """通过 LangGraph native stream 消费 custom/updates，再输出既有 SSE 协议。"""
        event_queue: Queue[AgentRuntimeEvent | None] = Queue()
        cancellation = Event()
        sequence = RuntimeEventSequence(request.run_code)
        queue_sink = QueueAgentEventSink(event_queue, cancellation)
        queue_emitter = RuntimeEventEmitter(request, queue_sink, sequence)
        graph_sink = LangGraphStreamEventSink(cancellation)
        graph_emitter = RuntimeEventEmitter(request, graph_sink, sequence)

        def run_graph_stream() -> None:
            terminal_emitted = False

            def emit_terminal_once(
                event_type: str,
                *,
                status: str,
                message: str,
                payload: dict[str, Any],
            ) -> None:
                nonlocal terminal_emitted
                if terminal_emitted or cancellation.is_set():
                    return
                terminal_emitted = queue_emitter.emit(
                    event_type,
                    status=status,
                    message=message,
                    payload=payload,
                    terminal=True,
                )

            try:
                queue_emitter.emit(
                    "RUN_STARTED",
                    status="RUNNING",
                    message="Agent Runtime 开始执行",
                    payload={"streamingMode": STREAMING_MODE_LANGGRAPH},
                )
                recorder = AgentRunRecorder(request=request, event_emitter=graph_emitter)
                graph_recorder = recorder
                stream = self._graph.stream(
                    self._initial_state(request, recorder),
                    stream_mode=["custom", "updates"],
                )
                for chunk in stream:
                    if cancellation.is_set():
                        return
                    event = _runtime_event_from_stream_chunk(chunk)
                    if event is not None:
                        event_queue.put(event)
                        continue
                    graph_recorder = _recorder_from_update_chunk(chunk) or graph_recorder

                response = graph_recorder.to_response()
                if cancellation.is_set():
                    return
                if response.error_message:
                    emit_terminal_once(
                        "RUN_FAILED",
                        status="FAILED",
                        message=str(response.error_message),
                        payload={
                            "summary": response.summary,
                            "errorMessage": response.error_message,
                        },
                    )
                else:
                    emit_terminal_once(
                        "RUN_COMPLETED",
                        status="SUCCEEDED",
                        message=response.summary or "Agent Runtime 执行完成",
                        payload={
                            "summary": response.summary,
                            "recommendedActions": [
                                action.model_dump(by_alias=True)
                                for action in response.recommended_actions
                            ],
                        },
                    )
            except AgentRunCancelled:
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
                event_queue.put(None)

        worker = Thread(
            target=run_graph_stream,
            name=f"agent-langgraph-stream-{request.run_code}",
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
            cancellation.set()

    def _initial_state(
        self,
        request: AgentRuntimeRequest,
        recorder: AgentRunRecorder,
    ) -> dict[str, Any]:
        """Build the LangGraph input state for one run."""
        return {
            "request": request,
            "tool_client": self._tool_client,
            "recorder": recorder,
            "catalog": LangChainToolCatalog(tool_client=self._tool_client, request=request),
            "messages": [],
            "pending_tool_call": None,
            "pending_action_call": None,
            "tool_call_count": 0,
        }


@lru_cache
def get_agent_runtime() -> AgentRuntime:
    """缓存 Runtime，复用已编译图和底层工具客户端。"""
    return AgentRuntime()


def _resolve_tool_client_name(value: str | None) -> str:
    """归一化工具客户端配置，空值默认使用 mcp。"""
    return (value or "mcp").strip().lower()


def _resolve_streaming_mode(value: str | None) -> str:
    """归一化 Agent streaming 实现模式。"""
    mode = (value or STREAMING_MODE_RECORDER).strip().lower()
    if mode in {STREAMING_MODE_RECORDER, STREAMING_MODE_LANGGRAPH}:
        return mode
    raise ValueError(f"Unsupported agent_streaming_mode: {value}")


def _runtime_event_from_stream_chunk(chunk: Any) -> AgentRuntimeEvent | None:
    """Extract an AgentRuntimeEvent from a LangGraph custom stream chunk."""
    mode, data = _split_stream_chunk(chunk)
    if mode != "custom" or not isinstance(data, dict):
        return None
    if data.get("type") != "agent_runtime_event":
        return None
    event = data.get("event")
    if not isinstance(event, dict):
        return None
    return AgentRuntimeEvent.model_validate(event)


def _recorder_from_update_chunk(chunk: Any) -> AgentRunRecorder | None:
    """Track the latest recorder from LangGraph updates without exposing state to Java."""
    mode, data = _split_stream_chunk(chunk)
    if mode != "updates" or not isinstance(data, dict):
        return None
    for update in data.values():
        if isinstance(update, dict) and isinstance(update.get("recorder"), AgentRunRecorder):
            return update["recorder"]
    return None


def _split_stream_chunk(chunk: Any) -> tuple[str | None, Any]:
    """Normalize LangGraph stream chunks for single or multiple stream modes."""
    if isinstance(chunk, tuple) and len(chunk) == 2 and isinstance(chunk[0], str):
        return chunk[0], chunk[1]
    if isinstance(chunk, dict) and isinstance(chunk.get("type"), str):
        return chunk["type"], chunk.get("data")
    return None, chunk


def _default_tool_client() -> AgentToolClient:
    """根据配置选择 MCP tools client。"""
    settings = get_settings()
    client_name = _resolve_tool_client_name(settings.agent_tool_client)
    if client_name == "mcp":
        return McpAgentToolClient(settings)
    raise ValueError(f"Unsupported agent_tool_client: {settings.agent_tool_client}")
