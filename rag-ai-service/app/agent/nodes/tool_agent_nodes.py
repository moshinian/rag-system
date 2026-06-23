from __future__ import annotations

from pydantic import ValidationError

from app.agent.graphs.state import AgentGraphState
from app.agent.graphs.steps import append_step, summarize_observation, to_json
from app.agent.planners.validation import tool_by_name, validate_decision
from app.agent.policies.risk import requires_confirmation
from app.agent.state import AgentActionDraft, AgentDecision, AgentObservation
from app.agent.tools.arguments import normalize_tool_arguments, tool_call_input_payload
from app.agent.tools.definitions import recommended_action_definitions


def load_tools(state: AgentGraphState) -> AgentGraphState:
    """加载当前可见工具，并初始化 planner 消息上下文。"""
    tools = state["tool_client"].definitions()
    next_state = dict(state)
    next_state["tools"] = tools
    # messages 暂时是最小上下文；接真实 LLM 时可在这里扩展系统约束和历史摘要。
    next_state["messages"] = [
        {
            "role": "system",
            "content": "You are a single intelligent Tool-use Agent. Return strict AgentDecision JSON only.",
        },
        {"role": "user", "content": state["request"].goal},
    ]
    return next_state


def llm_plan(state: AgentGraphState) -> AgentGraphState:
    """执行一轮 planner 决策，并强制校验 AgentDecision 协议。"""
    next_state = dict(state)
    decision_client = state["decision_client"]
    last_error: str | None = None

    for attempt in range(2):
        try:
            raw_decision = decision_client.decide(next_state)
            decision = AgentDecision.model_validate_json(raw_decision)
            # 所有 planner 输出都必须先过工具白名单和参数 schema 校验。
            validate_decision(decision, state.get("tools", []), state["request"])
            next_state["decision"] = decision
            return append_step(
                next_state,
                "llm_plan",
                step_type="LLM_DECISION",
                status="SUCCEEDED",
                output={
                    "attempt": attempt + 1,
                    "decision": decision.model_dump(by_alias=True),
                    "validated": True,
                },
            )
        except (ValidationError, ValueError) as exc:
            # 非法 JSON 或非法决策只重试一次，避免 Agent 无限自修复。
            last_error = str(exc)
            next_state["planner_error_message"] = last_error
        except Exception as exc:
            last_error = str(exc)
            break

    next_state["error_message"] = f"Invalid AgentDecision JSON: {last_error}"
    return append_step(
        next_state,
        "llm_plan",
        step_type="LLM_DECISION",
        status="FAILED",
        output={"validated": False, "errorSummary": last_error},
        error_message=next_state["error_message"],
    )


def route_decision(state: AgentGraphState) -> AgentGraphState:
    """执行路由前的通用防御校验。"""
    if state.get("error_message"):
        return state
    next_state = dict(state)
    if state.get("decision") is None:
        next_state["error_message"] = "Missing AgentDecision"
        return next_state
    if state["decision"].action == "CALL_TOOL" and int(state.get("tool_call_count") or 0) >= 6:
        # 工具调用次数上限只拦截继续调用工具；最终回答不能被误判为失败。
        next_state["error_message"] = "Exceeded max tool call count: 6"
        return next_state
    return state


def execute_readonly_tool(state: AgentGraphState) -> AgentGraphState:
    """执行已通过风险策略的只读工具，并记录 observation。"""
    decision = state.get("decision")
    if decision is None or decision.tool_name is None:
        next_state = dict(state)
        next_state["error_message"] = "CALL_TOOL decision must include toolName"
        return next_state

    normalized_arguments = normalize_tool_arguments(state["request"], decision.arguments)
    execution = state["tool_client"].execute(decision.tool_name, state["request"], normalized_arguments.arguments)
    raw_output = execution.output if isinstance(execution.output, dict) else {}
    # observation 是给下一轮 planner 的裁剪后工具反馈，不等同于完整 step output。
    observation = AgentObservation(
        toolName=decision.tool_name,
        success=execution.success,
        output=raw_output,
        summary=summarize_observation(raw_output),
        errorMessage=execution.error_message,
        durationMs=execution.duration_ms,
    )
    tool_results = dict(state.get("tool_results", {}))
    # tool_results 保留完整输出，便于最终报告或后续节点读取。
    tool_results[decision.tool_name] = {
        "success": execution.success,
        "output": raw_output,
        "summary": observation.summary,
        "errorMessage": execution.error_message,
        "durationMs": execution.duration_ms,
    }

    next_state = dict(state)
    next_state["tool_results"] = tool_results
    next_state["observations"] = [*state.get("observations", []), observation]
    next_state["tool_call_count"] = int(state.get("tool_call_count") or 0) + 1
    if not execution.success:
        next_state["error_message"] = execution.error_message or f"{decision.tool_name} failed"

    return append_step(
        next_state,
        "execute_readonly_tool",
        tool_name=decision.tool_name,
        step_type="TOOL_CALL",
        status="SUCCEEDED" if execution.success else "FAILED",
        input_json=to_json(tool_call_input_payload(normalized_arguments)),
        output={
            "raw": raw_output,
            "summaryForLlm": observation.summary,
        },
        duration_ms=execution.duration_ms,
        error_message=execution.error_message,
    )


def create_recommended_action(state: AgentGraphState) -> AgentGraphState:
    """把高风险/写操作决策转换为待确认 action 草案。"""
    decision = state.get("decision")
    if decision is None or decision.tool_name is None:
        next_state = dict(state)
        next_state["error_message"] = "REQUEST_CONFIRMATION decision must include toolName"
        return next_state

    action_definition = tool_by_name(recommended_action_definitions(), decision.tool_name)
    if action_definition is None:
        next_state = dict(state)
        next_state["error_message"] = f"Unknown recommended action: {decision.tool_name}"
        return next_state
    risk_level = action_definition.risk_level
    action = AgentActionDraft(
        tool_name=decision.tool_name,
        title=f"确认执行 {decision.tool_name}",
        reason=decision.reason,
        risk_level=risk_level,
        requires_confirmation=True,
        action_payload=to_json(decision.arguments or {"kbCode": state["request"].kb_code}),
    )
    next_state = dict(state)
    next_state["recommended_actions"] = [*state.get("recommended_actions", []), action]
    next_state["summary"] = f"Agent 已生成待确认动作：{decision.tool_name}"
    return append_step(
        next_state,
        "create_recommended_action",
        tool_name=decision.tool_name,
        step_type="NODE",
        status="SUCCEEDED",
        output={"recommendedAction": action.model_dump(by_alias=True)},
    )


def final_report(state: AgentGraphState) -> AgentGraphState:
    """根据 FINAL_ANSWER 决策生成最终报告。"""
    decision = state.get("decision")
    summary = decision.final_answer if decision and decision.final_answer else "智能 Agent 已完成。"
    next_state = dict(state)
    next_state["summary"] = summary
    return append_step(next_state, "final_report", output={"summary": summary})


def fail_report(state: AgentGraphState) -> AgentGraphState:
    """把智能图中的错误状态转换为失败 step 和 summary。"""
    error_message = state.get("error_message") or "智能 Agent 执行失败。"
    next_state = dict(state)
    next_state["summary"] = f"智能 Agent 执行失败：{error_message}"
    next_state["error_message"] = error_message
    return append_step(next_state, "fail_report", status="FAILED", output={"summary": next_state["summary"]})


def route_after_decision(state: AgentGraphState) -> str:
    """根据 planner 决策和风险策略选择下一跳。"""
    if state.get("error_message"):
        return "fail_report"
    decision = state.get("decision")
    if decision is None:
        return "fail_report"
    if decision.action == "FINAL_ANSWER":
        return "final_report"
    if decision.action == "REQUEST_CONFIRMATION":
        return "create_recommended_action"
    if decision.action == "CALL_TOOL":
        tool = tool_by_name(state.get("tools", []), decision.tool_name or "")
        if tool is None:
            state["error_message"] = f"Unknown toolName: {decision.tool_name}"
            return "fail_report"
        if requires_confirmation(tool):
            # 写操作、中高风险或显式 requiresConfirmation 的工具统一转人工确认。
            return "create_recommended_action"
        return "execute_readonly_tool"
    state["error_message"] = f"Unsupported AgentDecision action: {decision.action}"
    return "fail_report"
