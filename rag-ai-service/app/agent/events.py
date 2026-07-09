from __future__ import annotations

import json
from datetime import datetime, timezone
from queue import Queue
from threading import Event, Lock
from typing import Any, Protocol

from langgraph.config import get_stream_writer

from app.agent.state import AgentRuntimeEvent, AgentRuntimeEventType


class AgentRunCancelled(RuntimeError):
    """SSE 客户端断开后用于协作终止后续 LangGraph 节点的内部异常。"""


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


class LangGraphStreamEventSink:
    """把 Runtime 事件写入 LangGraph custom stream。"""

    def __init__(self, cancellation: Event) -> None:
        self._cancellation = cancellation

    def emit(self, event: AgentRuntimeEvent) -> bool:
        """在 LangGraph node 内把事件投递给原生 custom stream。"""
        if self._cancellation.is_set():
            return False
        writer = get_stream_writer()
        writer(
            {
                "type": "agent_runtime_event",
                "event": event.model_dump(by_alias=True),
            }
        )
        return True

    def is_cancelled(self) -> bool:
        """返回 graph stream 是否已取消。"""
        return self._cancellation.is_set()


class RuntimeEventEmitter:
    """生成并输出面向 Java 的 runtime 事件，不暴露 graph 内部状态。"""

    def __init__(self, request: Any, sink: AgentEventSink, sequence: RuntimeEventSequence) -> None:
        self._request = request
        self._sink = sink
        self._sequence = sequence

    def next_node_invocation_id(self) -> str:
        """为一个逻辑 step 返回稳定的 correlation id。"""
        return self._sequence.next_node_invocation_id()

    def emit(
        self,
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
        """在 stream 仍然活跃时输出一条 runtime 事件。"""
        if self._sink.is_cancelled():
            return False
        # eventId 和 createdAt 由 Python Runtime 生成，Java 负责落库和状态归并。
        event = AgentRuntimeEvent(
            eventId=self._sequence.next_event_id(),
            runCode=self._request.run_code,
            type=event_type,
            nodeInvocationId=node_invocation_id,
            nodeName=node_name,
            toolName=tool_name,
            status=status,
            message=message,
            payload=payload or {},
            terminal=terminal,
            createdAt=datetime.now(timezone.utc).isoformat(),
        )
        return self._sink.emit(event)

    def is_cancelled(self) -> bool:
        """返回 SSE 消费端是否已经断开。"""
        return self._sink.is_cancelled()

    def raise_if_cancelled(self) -> None:
        """流关闭后抛出内部取消哨兵异常。"""
        if self.is_cancelled():
            raise AgentRunCancelled("Agent SSE stream was cancelled")


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
