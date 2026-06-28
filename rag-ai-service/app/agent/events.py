from __future__ import annotations

import json
from collections.abc import Callable
from datetime import datetime, timezone
from queue import Queue
from threading import Event, Lock
from typing import Any, Protocol, TypeVar, cast

from app.agent.state import AgentRuntimeEvent, AgentRuntimeEventType, AgentStepResult


class AgentRunCancelled(RuntimeError):
    """SSE 客户端断开后用于协作终止后续 LangGraph node 的内部异常。"""


class AgentEventSink(Protocol):
    """Agent Runtime 事件接收器协议。"""

    def emit(self, event: AgentRuntimeEvent) -> bool:
        """接收事件；已取消时返回 False。"""

    def is_cancelled(self) -> bool:
        """返回当前 stream 是否已取消。"""


class RuntimeEventSequence:
    """为单个 run 生成稳定递增的 eventId 和 nodeInvocationId。"""

    def __init__(self, run_code: str) -> None:
        self._run_code = run_code
        self._event_sequence = 0
        self._node_sequence = 0
        self._lock = Lock()

    def next_event_id(self) -> str:
        """生成下一条 Runtime eventId。"""
        with self._lock:
            self._event_sequence += 1
            return f"{self._run_code}-{self._event_sequence:06d}"

    def next_node_invocation_id(self) -> str:
        """生成下一次 node 调用的 correlation id。"""
        with self._lock:
            self._node_sequence += 1
            return f"{self._run_code}-N-{self._node_sequence:06d}"


class QueueAgentEventSink:
    """把 Runtime 事件写入线程安全 Queue 的 sink。"""

    def __init__(
        self,
        queue: Queue[AgentRuntimeEvent | None],
        cancellation: Event,
    ) -> None:
        self._queue = queue
        self._cancellation = cancellation

    def emit(self, event: AgentRuntimeEvent) -> bool:
        """客户端断开后停止排队新事件。"""
        if self._cancellation.is_set():
            return False
        self._queue.put(event)
        return True

    def is_cancelled(self) -> bool:
        """返回 generator 是否已通知取消。"""
        return self._cancellation.is_set()


NodeFunction = TypeVar("NodeFunction", bound=Callable[[dict[str, Any]], dict[str, Any]])


def traced_node(node_name: str, function: NodeFunction) -> NodeFunction:
    """包装 LangGraph node，统一发布 STEP_STARTED/COMPLETED/FAILED。"""

    def wrapper(state: dict[str, Any]) -> dict[str, Any]:
        sink = _event_sink(state)
        if sink is None:
            # 旧 JSON run 未注入 sink，继续保持原有执行行为。
            return function(state)
        if sink.is_cancelled():
            raise AgentRunCancelled("Agent SSE stream was cancelled")

        sequence = _event_sequence(state)
        invocation_id = sequence.next_node_invocation_id()
        invocation_state = dict(state)
        invocation_state["current_node_invocation_id"] = invocation_id
        invocation_state["current_node_name"] = node_name
        emit_runtime_event(
            invocation_state,
            "STEP_STARTED",
            status="RUNNING",
            message=f"{node_name} 开始执行",
        )

        try:
            result = function(invocation_state)
            if sink.is_cancelled():
                raise AgentRunCancelled("Agent SSE stream was cancelled")
            result["current_node_invocation_id"] = invocation_id
            result["current_node_name"] = node_name
            step = _latest_node_step(result, node_name)
            event_type: AgentRuntimeEventType = (
                "STEP_FAILED" if step is not None and step.status == "FAILED" else "STEP_COMPLETED"
            )
            emit_runtime_event(
                result,
                event_type,
                tool_name=step.tool_name if step is not None else None,
                status=step.status if step is not None else "SUCCEEDED",
                message=(
                    step.error_message
                    if step is not None and step.error_message
                    else f"{node_name} 执行完成"
                ),
                payload=_step_payload(step),
            )
            return result
        except AgentRunCancelled:
            raise
        except Exception as exc:
            emit_runtime_event(
                invocation_state,
                "STEP_FAILED",
                status="FAILED",
                message=str(exc),
                payload={"errorMessage": str(exc)},
            )
            raise

    return cast(NodeFunction, wrapper)


def emit_runtime_event(
    state: dict[str, Any],
    event_type: AgentRuntimeEventType,
    *,
    node_name: str | None = None,
    node_invocation_id: str | None = None,
    tool_name: str | None = None,
    status: str | None = None,
    message: str | None = None,
    payload: dict[str, Any] | None = None,
    terminal: bool = False,
) -> bool:
    """使用 state 中的 sink 和序列生成并发布一条安全事件。"""
    sink = _event_sink(state)
    if sink is None or sink.is_cancelled():
        return False
    request = state["request"]
    event = AgentRuntimeEvent(
        eventId=_event_sequence(state).next_event_id(),
        runCode=request.run_code,
        type=event_type,
        nodeInvocationId=node_invocation_id or state.get("current_node_invocation_id"),
        nodeName=node_name or state.get("current_node_name"),
        toolName=tool_name,
        status=status,
        message=message,
        payload=payload or {},
        terminal=terminal,
        createdAt=datetime.now(timezone.utc).isoformat(),
    )
    return sink.emit(event)


def format_sse(event: AgentRuntimeEvent) -> str:
    """把 Runtime event 格式化为标准 SSE frame。"""
    data = json.dumps(
        event.model_dump(by_alias=True),
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return f"id: {event.event_id}\nevent: {event.type}\ndata: {data}\n\n"


def heartbeat_sse() -> str:
    """返回不进入业务协议的 SSE comment heartbeat。"""
    return ": heartbeat\n\n"


def _event_sink(state: dict[str, Any]) -> AgentEventSink | None:
    value = state.get("event_sink")
    return cast(AgentEventSink | None, value)


def _event_sequence(state: dict[str, Any]) -> RuntimeEventSequence:
    value = state.get("event_sequence")
    if not isinstance(value, RuntimeEventSequence):
        raise RuntimeError("Agent event sequence is missing")
    return value


def _latest_node_step(
    state: dict[str, Any],
    node_name: str,
) -> AgentStepResult | None:
    for step in reversed(state.get("steps", [])):
        if isinstance(step, AgentStepResult) and step.node_name == node_name:
            return step
    return None


def _step_payload(step: AgentStepResult | None) -> dict[str, Any]:
    if step is None:
        return {}
    output = _safe_json_object(step.output_json)
    payload: dict[str, Any] = {
        "stepType": step.step_type,
        "inputJson": step.input_json,
        "outputJson": step.output_json,
        "durationMs": step.duration_ms,
        "errorMessage": step.error_message,
    }
    if "attemptCount" in output:
        payload["attemptCount"] = output["attemptCount"]
    if "durationMs" in output and payload["durationMs"] is None:
        payload["durationMs"] = output["durationMs"]
    return payload


def _safe_json_object(value: str | None) -> dict[str, Any]:
    """从 step outputJson 中提取可提升到 STEP_FAILED payload 的稳定字段。"""
    if value is None:
        return {}
    try:
        parsed = json.loads(value)
    except ValueError:
        return {}
    return parsed if isinstance(parsed, dict) else {}
