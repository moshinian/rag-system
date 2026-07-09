from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from langchain_core.messages import AIMessage, BaseMessage
from pydantic import BaseModel, Field

from app.agent.events import RuntimeEventEmitter
from app.agent.state import AgentActionDraft, AgentRuntimeRequest, AgentRuntimeResponse, AgentStepResult
from app.agent.timeline import summarize_observation, to_json


class AgentFinalAnswer(BaseModel):
    """Structured final answer returned by an LLM node."""

    summary: str = Field(description="用户可见的最终诊断结论。")


@dataclass
class AgentRunRecorder:
    """Collect Java-facing steps/actions while optionally emitting runtime events."""

    request: AgentRuntimeRequest
    event_emitter: RuntimeEventEmitter | None = None
    steps: list[AgentStepResult] = field(default_factory=list)
    recommended_actions: list[AgentActionDraft] = field(default_factory=list)
    summary: str | None = None
    error_message: str | None = None
    _model_step_count: int = 0

    def ensure_not_cancelled(self) -> None:
        """Stop execution promptly when the SSE consumer disconnects."""
        if self.event_emitter is not None:
            self.event_emitter.raise_if_cancelled()

    def record_model_update(
        self,
        messages: list[BaseMessage],
        *,
        alias_to_canonical: dict[str, str],
        structured_response: AgentFinalAnswer | None = None,
    ) -> None:
        """Map a LangChain model update to the existing LLM_DECISION timeline."""
        ai_message = next((message for message in reversed(messages) if isinstance(message, AIMessage)), None)
        if ai_message is None and structured_response is None:
            return

        self._model_step_count += 1
        tool_calls = []
        for call in getattr(ai_message, "tool_calls", None) or []:
            call_name = call.get("name", "")
            if structured_response is not None and call_name == AgentFinalAnswer.__name__:
                continue
            tool_calls.append(
                {
                    "toolName": alias_to_canonical.get(call_name, call_name),
                    "arguments": call.get("args") or {},
                    "toolCallId": call.get("id"),
                }
            )
        output: dict[str, Any] = {
            "toolCalls": tool_calls,
            "messageId": getattr(ai_message, "id", None),
            "modelStep": self._model_step_count,
        }
        if structured_response is not None:
            self.summary = structured_response.summary
            output["structuredResponse"] = structured_response.model_dump()
        elif ai_message is not None and isinstance(ai_message.content, str) and ai_message.content.strip():
            self.summary = ai_message.content.strip()
            output["content"] = self.summary

        self.append_completed_step(
            node_name="agent_model",
            step_type="LLM_DECISION",
            output=output,
        )
        if tool_calls:
            self.emit(
                "PLANNER_DECISION",
                node_name="agent_model",
                status="SUCCEEDED",
                message="LangGraph agent_model node 选择工具调用",
                payload={"toolCalls": tool_calls, "modelStep": self._model_step_count},
            )

    def record_tool_execution(
        self,
        *,
        tool_name: str,
        normalized_input: dict[str, Any],
        output: dict[str, Any],
        success: bool,
        duration_ms: int,
        error_message: str | None,
    ) -> None:
        """Record a read-only tool call as Java-facing step and runtime events."""
        invocation_id = self.start_step("execute_readonly_tool", tool_name)
        summary = summarize_observation(output)
        self.emit(
            "TOOL_CALL_COMPLETED" if success else "TOOL_CALL_FAILED",
            node_name="execute_readonly_tool",
            node_invocation_id=invocation_id,
            tool_name=tool_name,
            status="SUCCEEDED" if success else "FAILED",
            message=error_message or f"{tool_name} 调用完成",
            payload={
                "success": success,
                "durationMs": duration_ms,
                "summary": summary,
                "errorMessage": error_message,
            },
        )
        self.emit(
            "OBSERVATION_CREATED",
            node_name="execute_readonly_tool",
            node_invocation_id=invocation_id,
            tool_name=tool_name,
            status="SUCCEEDED" if success else "FAILED",
            message=f"{tool_name} observation 已生成",
            payload={
                "success": success,
                "summary": summary,
                "durationMs": duration_ms,
                "errorMessage": error_message,
            },
        )
        step = AgentStepResult(
            node_name="execute_readonly_tool",
            tool_name=tool_name,
            step_type="TOOL_CALL",
            status="SUCCEEDED" if success else "FAILED",
            input_json=to_json(normalized_input),
            output_json=to_json({"raw": output, "summaryForLlm": summary}),
            duration_ms=duration_ms,
            error_message=error_message,
        )
        self.steps.append(step)
        self.complete_step(invocation_id, step)
        if not success:
            self.error_message = error_message or f"{tool_name} failed"

    def record_recommended_action(
        self,
        *,
        action: AgentActionDraft,
        output: dict[str, Any],
    ) -> None:
        """Record a Java-confirmed action recommendation."""
        invocation_id = self.start_step("create_recommended_action", action.tool_name)
        self.recommended_actions.append(action)
        self.summary = f"Agent 已生成待确认动作：{action.tool_name}"
        self.emit(
            "ACTION_RECOMMENDED",
            node_name="create_recommended_action",
            node_invocation_id=invocation_id,
            tool_name=action.tool_name,
            status="PENDING_CONFIRMATION",
            message=action.title,
            payload=action.model_dump(by_alias=True),
        )
        step = AgentStepResult(
            node_name="create_recommended_action",
            tool_name=action.tool_name,
            step_type="NODE",
            status="SUCCEEDED",
            output_json=to_json(output),
        )
        self.steps.append(step)
        self.complete_step(invocation_id, step)

    def record_node_failure(self, *, node_name: str, error_message: str) -> None:
        """Record a failed graph node without leaking exceptions across the Java boundary."""
        self.error_message = error_message
        if self.steps and self.steps[-1].status == "FAILED":
            return
        self.append_completed_step(
            node_name=node_name,
            step_type="NODE",
            status="FAILED",
            output={"errorMessage": error_message},
            error_message=error_message,
        )

    def to_response(self) -> AgentRuntimeResponse:
        """Build the Java-facing runtime response."""
        return AgentRuntimeResponse(
            status="FAILED" if self.error_message else "SUCCEEDED",
            summary=self.summary,
            steps=self.steps,
            recommended_actions=self.recommended_actions,
            error_message=self.error_message,
        )

    def append_completed_step(
        self,
        *,
        node_name: str,
        step_type: str,
        output: dict[str, Any],
        status: str = "SUCCEEDED",
        error_message: str | None = None,
    ) -> None:
        invocation_id = self.start_step(node_name, None)
        step = AgentStepResult(
            node_name=node_name,
            step_type=step_type,
            status=status,
            output_json=to_json(output),
            error_message=error_message,
        )
        self.steps.append(step)
        self.complete_step(invocation_id, step)

    def start_step(self, node_name: str, tool_name: str | None) -> str | None:
        if self.event_emitter is None:
            return None
        invocation_id = self.event_emitter.next_node_invocation_id()
        self.event_emitter.emit(
            "STEP_STARTED",
            node_name=node_name,
            node_invocation_id=invocation_id,
            tool_name=tool_name,
            status="RUNNING",
            message=f"{node_name} 开始执行",
        )
        return invocation_id

    def complete_step(self, invocation_id: str | None, step: AgentStepResult) -> None:
        self.emit(
            "STEP_FAILED" if step.status == "FAILED" else "STEP_COMPLETED",
            node_name=step.node_name,
            node_invocation_id=invocation_id,
            tool_name=step.tool_name,
            status=step.status,
            message=step.error_message or f"{step.node_name} 执行完成",
            payload={
                "stepType": step.step_type,
                "inputJson": step.input_json,
                "outputJson": step.output_json,
                "durationMs": step.duration_ms,
                "errorMessage": step.error_message,
            },
        )

    def emit(self, event_type: str, **kwargs: Any) -> None:
        if self.event_emitter is not None:
            self.event_emitter.emit(event_type, **kwargs)
