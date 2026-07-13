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
    """创建执行单次 LangChain 模型调用的 LangGraph 节点。"""

    def node(state: AgentGraphState) -> AgentGraphState:
        recorder = _recorder(state)
        recorder.ensure_not_cancelled()
        try:
            catalog = _catalog(state)
            messages = _messages(state)
            model = chat_model or _build_chat_model(settings)
            # 将 Java 暴露的只读工具、待确认动作工具和最终回答结构统一绑定给模型。
            bound_model = model.bind_tools([*catalog.tools(), AgentFinalAnswer])
            ai_output = bound_model.invoke(messages)
            structured = _structured_final_answer(ai_output)
            # 兼容支持 structured output 的模型实现：它可能直接返回结构化对象而不是 AIMessage。
            if structured is not None and not isinstance(ai_output, AIMessage):
                recorder.record_model_update(
                    [],
                    alias_to_canonical=catalog.alias_to_canonical,
                    structured_response=structured,
                )
                return {
                    **state,
                    "pending_tool_calls": [],
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

            # 最终回答和工具调用互斥；如果已经拿到最终回答，就结束本轮 graph。
            tool_calls = _non_final_tool_calls(ai_message)
            if structured is not None:
                return {
                    **state,
                    "messages": messages,
                    "pending_tool_calls": [],
                    "pending_action_call": None,
                }
            if not tool_calls:
                if recorder.summary:
                    return {
                        **state,
                        "messages": messages,
                        "pending_tool_calls": [],
                        "pending_action_call": None,
                    }
                raise ValueError("LangChain model did not return a tool call or final answer")

            action_calls = [call for call in tool_calls if catalog.is_action_alias(str(call.get("name") or ""))]
            if action_calls:
                if len(tool_calls) > 1:
                    raise ValueError("Read-only tool calls and action request tool calls must be separated")
                return {
                    **state,
                    "messages": messages,
                    "pending_tool_calls": [],
                    "pending_action_call": action_calls[0],
                }
            return {
                **state,
                "messages": messages,
                "pending_tool_calls": tool_calls,
                "pending_action_call": None,
            }
        except Exception as exc:
            recorder.record_node_failure(node_name="agent_model", error_message=str(exc))
            return {
                **state,
                "pending_tool_calls": [],
                "pending_action_call": None,
            }

    return node


def execute_readonly_tool_node(state: AgentGraphState) -> AgentGraphState:
    """执行模型在同一条消息里选择的全部只读工具。"""
    recorder = _recorder(state)
    recorder.ensure_not_cancelled()
    calls = list(state.get("pending_tool_calls") or [])
    if not calls:
        return state

    catalog = _catalog(state)
    try:
        previous_count = int(state.get("tool_call_count") or 0)
        new_count = previous_count + len(calls)
        if new_count > MAX_TOOL_CALLS:
            raise ValueError(f"Maximum tool call count exceeded: {MAX_TOOL_CALLS}")

        tool_messages: list[ToolMessage] = []
        for index, call in enumerate(calls, start=1):
            started_at = perf_counter()
            alias = str(call.get("name") or "")
            args = dict(call.get("args") or {})
            definition = catalog.definition_for_alias(alias)
            canonical_name = catalog.canonical_name(alias)
            if definition is None:
                raise ValueError(f"Unknown tool: {alias}")
            if requires_confirmation(definition):
                raise ValueError(f"Tool requires confirmation and cannot be executed directly: {canonical_name}")

            # 先做 Python 侧轻量校验，Java MCP server 仍然是最终参数校验边界。
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
            # 工具调用始终通过 AgentToolClient 进入 Java/MCP 边界，不在 Python 内直接操作业务状态。
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
                    "pending_tool_calls": [],
                    "pending_action_call": None,
                    "tool_call_count": previous_count + index,
                }

            # 每一个 assistant tool_call 都必须紧跟一条同 id 的 ToolMessage，符合 LangChain/LangGraph 标准消息协议。
            tool_messages.append(
                ToolMessage(
                    content=to_json(output),
                    tool_call_id=str(call.get("id") or f"{alias}-{previous_count + index}"),
                    name=alias,
                )
            )

        return {
            **state,
            "messages": [*_messages(state), *tool_messages],
            "pending_tool_calls": [],
            "pending_action_call": None,
            "tool_call_count": new_count,
        }
    except Exception as exc:
        recorder.record_node_failure(node_name="execute_readonly_tool", error_message=str(exc))
        return {
            **state,
            "pending_tool_calls": [],
            "pending_action_call": None,
        }


def create_recommended_action_node(state: AgentGraphState) -> AgentGraphState:
    """把模型的写操作意图转换成 Java 侧待确认动作草案。"""
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
        # 待确认动作也要先按工具 schema 校验，避免把畸形 payload 传给 Java。
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
        "pending_tool_calls": [],
        "pending_action_call": None,
    }


def final_response_node(state: AgentGraphState) -> AgentGraphState:
    """成功生成最终回答后的显式 LangGraph 终点。"""
    return state


def _build_chat_model(settings: Settings) -> ChatOpenAI:
    """构造图节点内使用的 OpenAI 兼容 LangChain chat 模型。"""
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
        "你是一个运行在 LangGraph 节点中的单 Agent RAG 运维工具调用助手。"
        "你只能使用已提供的工具诊断 Java RAG 系统。"
        "只读检查工具可以直接调用。"
        "任何重试、重建、写操作，或中高风险操作，都必须通过 request_* 工具发起，"
        "由它生成 Java 人工确认动作草案，不能在 Python Runtime 内直接执行。"
        "不得自行执行 shell、HTTP、数据库写入、重试或重建。"
        "不得暴露思维链，只输出面向用户的结论、证据和建议。"
        f"权威 runCode 是 {request.run_code}；权威 kbCode 是 {request.kb_code}。"
    )


def _user_prompt(state: AgentGraphState) -> str:
    request = _request(state)
    question = f"\n问题: {request.question}" if request.question else ""
    return f"目标: {request.goal}\n知识库编码: {request.kb_code}{question}"


def _messages(state: AgentGraphState) -> list:
    existing = state.get("messages")
    if existing:
        return list(existing)
    return [
        SystemMessage(content=_system_prompt(state)),
        HumanMessage(content=_user_prompt(state)),
    ]


def _structured_final_answer(output: Any) -> AgentFinalAnswer | None:
    """从工具调用或结构化输出兼容结构中提取最终回答。"""
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


def _non_final_tool_calls(message: AIMessage) -> list[dict[str, Any]]:
    """返回所有非最终回答工具调用。"""
    result: list[dict[str, Any]] = []
    for call in getattr(message, "tool_calls", None) or []:
        if call.get("name") != AgentFinalAnswer.__name__:
            result.append(dict(call))
    return result


def _catalog(state: AgentGraphState) -> LangChainToolCatalog:
    """读取当前图状态中的工具目录。"""
    catalog = state.get("catalog")
    if catalog is None:
        raise RuntimeError("Agent graph catalog is missing")
    return catalog


def _request(state: AgentGraphState):
    """读取 Java 传入的 Runtime 请求。"""
    return state["request"]


def _recorder(state: AgentGraphState) -> AgentRunRecorder:
    """读取当前 run 的事件和 step 记录器。"""
    return state["recorder"]


def _tool_client(state: AgentGraphState):
    """读取当前 graph 使用的工具客户端。"""
    return state["tool_client"]


def _elapsed_ms(started_at: float) -> int:
    """计算从指定起点到现在的毫秒耗时。"""
    return max(0, int((perf_counter() - started_at) * 1000))
