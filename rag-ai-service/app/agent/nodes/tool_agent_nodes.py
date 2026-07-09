from __future__ import annotations

from time import perf_counter
from typing import Any

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI

from app.agent.graphs.state import AgentGraphState
from app.agent.policies.risk import requires_confirmation
from app.agent.recording import AgentFinalAnswer, AgentRunRecorder
from app.agent.state import AgentActionDraft
from app.agent.timeline import to_json
from app.agent.tools.arguments import normalize_tool_arguments, tool_call_input_payload
from app.agent.tools.catalog import LangChainToolCatalog
from app.agent.tools.schema import validate_arguments
from app.core.config import Settings

MAX_TOOL_CALLS = 8


def agent_model_node(
    *,
    settings: Settings,
    chat_model: Any | None,
):
    """Create the LangGraph node that performs one LangChain model call."""

    def node(state: AgentGraphState) -> AgentGraphState:
        recorder = _recorder(state)
        recorder.ensure_not_cancelled()
        try:
            catalog = _catalog(state)
            messages = _messages(state)
            model = chat_model or _build_chat_model(settings)
            bound_model = model.bind_tools([*catalog.tools(), AgentFinalAnswer])
            ai_output = bound_model.invoke(messages)
            structured = _structured_final_answer(ai_output)
            if structured is not None and not isinstance(ai_output, AIMessage):
                recorder.record_model_update(
                    [],
                    alias_to_canonical=catalog.alias_to_canonical,
                    structured_response=structured,
                )
                return {
                    **state,
                    "pending_tool_call": None,
                    "pending_action_call": None,
                }

            ai_message = ai_output
            if not isinstance(ai_message, AIMessage):
                raise ValueError("LangChain model did not return an AIMessage")

            messages = [*messages, ai_message]
            recorder.record_model_update(
                [ai_message],
                alias_to_canonical=catalog.alias_to_canonical,
                structured_response=structured,
            )

            pending_call = _first_non_final_tool_call(ai_message)
            if structured is not None:
                return {
                    **state,
                    "messages": messages,
                    "pending_tool_call": None,
                    "pending_action_call": None,
                }
            if pending_call is None:
                if recorder.summary:
                    return {
                        **state,
                        "messages": messages,
                        "pending_tool_call": None,
                        "pending_action_call": None,
                    }
                raise ValueError("LangChain model did not return a tool call or final answer")

            alias = str(pending_call.get("name") or "")
            if catalog.is_action_alias(alias):
                return {
                    **state,
                    "messages": messages,
                    "pending_tool_call": None,
                    "pending_action_call": pending_call,
                }
            return {
                **state,
                "messages": messages,
                "pending_tool_call": pending_call,
                "pending_action_call": None,
            }
        except Exception as exc:
            recorder.record_node_failure(node_name="agent_model", error_message=str(exc))
            return {
                **state,
                "pending_tool_call": None,
                "pending_action_call": None,
            }

    return node


def execute_readonly_tool_node(state: AgentGraphState) -> AgentGraphState:
    """Execute one read-only tool selected by the model."""
    recorder = _recorder(state)
    recorder.ensure_not_cancelled()
    call = state.get("pending_tool_call")
    if not call:
        return state

    started_at = perf_counter()
    catalog = _catalog(state)
    alias = str(call.get("name") or "")
    args = dict(call.get("args") or {})
    definition = catalog.definition_for_alias(alias)
    canonical_name = catalog.canonical_name(alias)
    try:
        if definition is None:
            raise ValueError(f"Unknown tool: {alias}")
        if requires_confirmation(definition):
            raise ValueError(f"Tool requires confirmation and cannot be executed directly: {canonical_name}")
        count = int(state.get("tool_call_count") or 0) + 1
        if count > MAX_TOOL_CALLS:
            raise ValueError(f"Maximum tool call count exceeded: {MAX_TOOL_CALLS}")

        normalized = normalize_tool_arguments(_request(state), args)
        validate_arguments(normalized.arguments, definition.input_schema)
        recorder.emit(
            "TOOL_CALL_STARTED",
            node_name="execute_readonly_tool",
            tool_name=canonical_name,
            status="RUNNING",
            message=f"{canonical_name} 开始调用",
            payload={"arguments": normalized.arguments},
        )
        execution = _tool_client(state).execute(canonical_name, _request(state), normalized.arguments)
        output = execution.output if isinstance(execution.output, dict) else {}
        duration_ms = execution.duration_ms if execution.duration_ms is not None else _elapsed_ms(started_at)
        recorder.record_tool_execution(
            tool_name=canonical_name,
            normalized_input=tool_call_input_payload(normalized),
            output=output,
            success=execution.success,
            duration_ms=duration_ms,
            error_message=execution.error_message,
        )
        if not execution.success:
            return {
                **state,
                "pending_tool_call": None,
                "pending_action_call": None,
                "tool_call_count": count,
            }

        messages = [
            *_messages(state),
            ToolMessage(
                content=to_json(output),
                tool_call_id=str(call.get("id") or f"{alias}-{count}"),
                name=alias,
            ),
        ]
        return {
            **state,
            "messages": messages,
            "pending_tool_call": None,
            "pending_action_call": None,
            "tool_call_count": count,
        }
    except Exception as exc:
        recorder.record_node_failure(node_name="execute_readonly_tool", error_message=str(exc))
        return {
            **state,
            "pending_tool_call": None,
            "pending_action_call": None,
        }


def create_recommended_action_node(state: AgentGraphState) -> AgentGraphState:
    """Convert a model write intent into a Java-confirmed action draft."""
    recorder = _recorder(state)
    recorder.ensure_not_cancelled()
    call = state.get("pending_action_call")
    if not call:
        return state

    catalog = _catalog(state)
    alias = str(call.get("name") or "")
    args = dict(call.get("args") or {})
    definition = catalog.definition_for_alias(alias)
    try:
        if definition is None:
            raise ValueError(f"Unknown action request tool: {alias}")
        validate_arguments(args, definition.input_schema)
        action = AgentActionDraft(
            tool_name=definition.name,
            title=f"确认执行 {definition.name}",
            reason=str(args.get("reason") or definition.description),
            risk_level=definition.risk_level,
            requires_confirmation=True,
            action_payload=to_json(args or {"kbCode": _request(state).kb_code}),
        )
        output = {"recommendedAction": action.model_dump(by_alias=True)}
        recorder.record_recommended_action(action=action, output=output)
    except Exception as exc:
        recorder.record_node_failure(node_name="create_recommended_action", error_message=str(exc))
    return {
        **state,
        "pending_tool_call": None,
        "pending_action_call": None,
    }


def final_response_node(state: AgentGraphState) -> AgentGraphState:
    """Explicit LangGraph endpoint for successful model completion."""
    return state


def _build_chat_model(settings: Settings) -> ChatOpenAI:
    """Build the OpenAI-compatible LangChain chat model used inside graph nodes."""
    return ChatOpenAI(
        model=settings.agent_planner_model or settings.chat_default_model,
        api_key=settings.chat_api_key or "not-set",
        base_url=settings.chat_base_url.rstrip("/") or None,
        temperature=settings.agent_planner_temperature,
        timeout=settings.agent_planner_timeout_ms / 1000,
        max_retries=2,
    )


def _system_prompt(state: AgentGraphState) -> str:
    request = _request(state)
    return (
        "You are a single RAG operations Tool-use Agent running inside a LangGraph node. "
        "Use the provided tools to inspect the Java RAG system. "
        "Read-only inspection tools may be called directly. "
        "Any retry, rebuild, write, or medium/high risk operation must be requested through a request_* tool, "
        "which creates a Java human-confirmed action draft instead of executing the operation. "
        "Never execute shell, HTTP, database writes, retries, or rebuilds yourself. "
        "Do not reveal chain-of-thought. "
        f"The authoritative runCode is {request.run_code}; the authoritative kbCode is {request.kb_code}."
    )


def _user_prompt(state: AgentGraphState) -> str:
    request = _request(state)
    question = f"\nquestion: {request.question}" if request.question else ""
    return f"goal: {request.goal}\nkbCode: {request.kb_code}{question}"


def _messages(state: AgentGraphState) -> list:
    existing = state.get("messages")
    if existing:
        return list(existing)
    return [
        SystemMessage(content=_system_prompt(state)),
        HumanMessage(content=_user_prompt(state)),
    ]


def _structured_final_answer(output: Any) -> AgentFinalAnswer | None:
    """Extract final answer from tool-calling or structured-output compatible shapes."""
    if isinstance(output, AgentFinalAnswer):
        return output
    if isinstance(output, dict):
        candidate = output.get("structured_response") or output.get("structuredResponse") or output
        if isinstance(candidate, AgentFinalAnswer):
            return candidate
        if isinstance(candidate, dict) and "summary" in candidate:
            return AgentFinalAnswer.model_validate(candidate)
    for attr in ("structured_response", "structuredResponse"):
        candidate = getattr(output, attr, None)
        if isinstance(candidate, AgentFinalAnswer):
            return candidate
        if isinstance(candidate, dict) and "summary" in candidate:
            return AgentFinalAnswer.model_validate(candidate)
    for container in (getattr(output, "additional_kwargs", None), getattr(output, "response_metadata", None)):
        if not isinstance(container, dict):
            continue
        candidate = container.get("structured_response") or container.get("structuredResponse")
        if isinstance(candidate, AgentFinalAnswer):
            return candidate
        if isinstance(candidate, dict) and "summary" in candidate:
            return AgentFinalAnswer.model_validate(candidate)
    for call in getattr(output, "tool_calls", None) or []:
        if call.get("name") == AgentFinalAnswer.__name__:
            return AgentFinalAnswer.model_validate(call.get("args") or {})
    return None


def _first_non_final_tool_call(message: AIMessage) -> dict[str, Any] | None:
    for call in getattr(message, "tool_calls", None) or []:
        if call.get("name") != AgentFinalAnswer.__name__:
            return dict(call)
    return None


def _catalog(state: AgentGraphState) -> LangChainToolCatalog:
    catalog = state.get("catalog")
    if catalog is None:
        raise RuntimeError("Agent graph catalog is missing")
    return catalog


def _request(state: AgentGraphState):
    return state["request"]


def _recorder(state: AgentGraphState) -> AgentRunRecorder:
    return state["recorder"]


def _tool_client(state: AgentGraphState):
    return state["tool_client"]


def _elapsed_ms(started_at: float) -> int:
    return max(0, int((perf_counter() - started_at) * 1000))
