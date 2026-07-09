from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from langchain_core.messages import AIMessage, BaseMessage
from pydantic import BaseModel, Field

from app.agent.events import RuntimeEventEmitter
from app.agent.state import AgentActionDraft, AgentRuntimeRequest, AgentRuntimeResponse, AgentStepResult
from app.agent.timeline import summarize_observation, to_json


class AgentFinalAnswer(BaseModel):
    """LLM 节点返回的结构化最终回答。"""

    summary: str = Field(description="用户可见的最终诊断结论。")


@dataclass
class AgentRunRecorder:
    """收集返回给 Java 的 steps/actions，并按需输出 runtime 事件。"""

    request: AgentRuntimeRequest
    event_emitter: RuntimeEventEmitter | None = None
    steps: list[AgentStepResult] = field(default_factory=list)
    recommended_actions: list[AgentActionDraft] = field(default_factory=list)
    summary: str | None = None
    error_message: str | None = None
    _model_step_count: int = 0

    def ensure_not_cancelled(self) -> None:
        """SSE 消费端断开后尽快停止后续执行。"""
        if self.event_emitter is not None:
            self.event_emitter.raise_if_cancelled()

    def record_model_update(
        self,
        messages: list[BaseMessage],
        *,
        alias_to_canonical: dict[str, str],
        structured_response: AgentFinalAnswer | None = None,
    ) -> None:
        """把 LangChain 模型更新映射到既有 LLM_DECISION 时间线。"""
        ai_message = next((message for message in reversed(messages) if isinstance(message, AIMessage)), None)
        if ai_message is None and structured_response is None:
            return

        self._model_step_count += 1
        tool_calls = []
        for call in getattr(ai_message, "tool_calls", None) or []:
            call_name = call.get("name", "")
            # AgentFinalAnswer 是最终回答结构，不作为普通工具调用展示给 Java/前端。
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
            # 某些模型可能不用工具结构返回最终文本，这里兼容纯文本最终回答。
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
        """把只读工具调用记录成 Java 可持久化 step 和 runtime 事件。"""
        invocation_id = self.start_step("execute_readonly_tool", tool_name)
        # 观察结果会裁剪成摘要，避免把完整工具输出重复塞给模型和时间线。
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
        """记录一个需要 Java 人工确认的推荐动作。"""
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
        """记录失败 graph 节点，避免异常直接跨过 Java 调用边界。"""
        self.error_message = error_message
        # 如果上一条 step 已经是失败工具调用，就不再追加重复失败节点。
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
        """构造返回给 Java 的 Runtime 响应。"""
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
        """追加一个已经完成的 step，并补齐对应的开始/完成事件。"""
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
        """开始一个逻辑 step，并在有事件输出器时生成 nodeInvocationId。"""
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
        """完成一个逻辑 step，并输出成功或失败事件。"""
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
        """在配置了 event emitter 时输出 Runtime 事件。"""
        if self.event_emitter is not None:
            self.event_emitter.emit(event_type, **kwargs)
