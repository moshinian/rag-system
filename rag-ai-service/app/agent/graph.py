from __future__ import annotations

import json
import warnings
from typing import Any, Protocol, TypedDict

from langchain_core._api.deprecation import LangChainPendingDeprecationWarning
from pydantic import ValidationError

warnings.filterwarnings(
    "ignore",
    message="The default value of `allowed_objects` will change in a future version.*",
    category=LangChainPendingDeprecationWarning,
    module="langgraph.cache.base",
)

from langgraph.graph import END, StateGraph

from app.agent.state import (
    AgentActionDraft,
    AgentDecision,
    AgentObservation,
    AgentRuntimeRequest,
    AgentStepResult,
    AgentToolDefinition,
)
from app.agent.tools import AgentToolClient


class AgentGraphState(TypedDict, total=False):
    request: AgentRuntimeRequest
    tool_client: AgentToolClient
    decision_client: "AgentDecisionClient"
    tools: list[AgentToolDefinition]
    messages: list[dict[str, Any]]
    decision: AgentDecision | None
    observations: list[AgentObservation]
    tool_call_count: int
    tool_results: dict[str, Any]
    steps: list[AgentStepResult]
    recommended_actions: list[AgentActionDraft]
    diagnosis: dict[str, Any]
    summary: str | None
    error_message: str | None


class AgentDecisionClient(Protocol):
    def decide(self, state: AgentGraphState) -> str:
        """Return a strict JSON AgentDecision string."""


class HeuristicAgentDecisionClient:
    """Deterministic local planner used until a real LLM planner is wired in."""

    def decide(self, state: AgentGraphState) -> str:
        goal_text = f"{state['request'].goal} {state['request'].question or ''}".lower()
        called_tools = {observation.tool_name for observation in state.get("observations", [])}

        if "项目状态" in goal_text or "git 状态" in goal_text or "repo status" in goal_text:
            if "mcp.repo.status.inspect" not in called_tools:
                return _decision_json("CALL_TOOL", "mcp.repo.status.inspect", "先通过 MCP 工具检查项目状态。")
            if "cli.git.status" not in called_tools:
                return _decision_json("CALL_TOOL", "cli.git.status", "再通过只读 CLI 工具检查 git 状态。")
            return _decision_json(
                "FINAL_ANSWER",
                None,
                "项目状态和 git 状态观察结果已足够生成结论。",
                final_answer="已完成项目状态和 git 状态检查，未执行任何写操作。",
            )

        if "索引异常" in goal_text or "索引失败" in goal_text or "failed indexing" in goal_text:
            if "indexing.tasks.scan" not in called_tools:
                return _decision_json("CALL_TOOL", "indexing.tasks.scan", "需要扫描索引任务状态。")
            indexing_observation = _observation_output(state, "indexing.tasks.scan")
            failed_tasks = _failed_tasks(indexing_observation)
            if failed_tasks:
                failed_task = failed_tasks[0]
                return _decision_json(
                    "CALL_TOOL",
                    "document.indexing_task.retry",
                    "发现 FAILED indexing task，需要转为待确认动作。",
                    arguments={
                        "kbCode": state["request"].kb_code,
                        "taskId": failed_task.get("taskId"),
                        "documentCode": failed_task.get("documentCode"),
                    },
                    risk_level="MEDIUM",
                )
            return _decision_json(
                "FINAL_ANSWER",
                None,
                "未发现失败索引任务。",
                final_answer="当前未发现 FAILED indexing task。",
            )

        if "不能问答" in goal_text or "不可问答" in goal_text or "readiness" in goal_text:
            if "kb.readiness.check" not in called_tools:
                return _decision_json("CALL_TOOL", "kb.readiness.check", "需要检查知识库 readiness。")
            readiness = _observation_output(state, "kb.readiness.check")
            if readiness.get("reembedRequired") is True:
                return _decision_json(
                    "CALL_TOOL",
                    "embedding.rebuild.submit",
                    "readiness 显示需要重嵌入，必须转为待确认动作。",
                    arguments={"kbCode": state["request"].kb_code},
                    risk_level="MEDIUM",
                )
            return _decision_json(
                "FINAL_ANSWER",
                None,
                "readiness 观察结果未发现阻断问题。",
                final_answer="当前未发现阻断问答的 readiness 问题。",
            )

        return _decision_json(
            "FINAL_ANSWER",
            None,
            "当前问题无需调用额外工具。",
            final_answer="当前智能 Agent 未识别到需要调用的工具。",
        )


def build_agent_graph():
    return build_readiness_diagnosis_graph()


def build_readiness_diagnosis_graph():
    workflow = StateGraph(AgentGraphState)
    workflow.add_node("parse_goal", parse_goal)
    workflow.add_node("system_health_check", system_health_check)
    workflow.add_node("kb_readiness_check", kb_readiness_check)
    workflow.add_node("documents_status_scan", documents_status_scan)
    workflow.add_node("indexing_tasks_scan", indexing_tasks_scan)
    workflow.add_node("qa_retrieve_probe", qa_retrieve_probe)
    workflow.add_node("diagnose", diagnose)
    workflow.add_node("recommend_actions", recommend_actions)
    workflow.add_node("generate_report", generate_report)

    workflow.set_entry_point("parse_goal")
    workflow.add_edge("parse_goal", "system_health_check")
    workflow.add_edge("system_health_check", "kb_readiness_check")
    workflow.add_edge("kb_readiness_check", "documents_status_scan")
    workflow.add_edge("documents_status_scan", "indexing_tasks_scan")
    workflow.add_edge("indexing_tasks_scan", "qa_retrieve_probe")
    workflow.add_edge("qa_retrieve_probe", "diagnose")
    workflow.add_edge("diagnose", "recommend_actions")
    workflow.add_edge("recommend_actions", "generate_report")
    workflow.add_edge("generate_report", END)
    return workflow.compile()


def build_intelligent_tool_agent_graph():
    workflow = StateGraph(AgentGraphState)
    workflow.add_node("load_tools", load_tools)
    workflow.add_node("llm_plan", llm_plan)
    workflow.add_node("route_decision", route_decision)
    workflow.add_node("execute_readonly_tool", execute_readonly_tool)
    workflow.add_node("create_recommended_action", create_recommended_action)
    workflow.add_node("final_report", final_report)
    workflow.add_node("fail_report", fail_report)

    workflow.set_entry_point("load_tools")
    workflow.add_edge("load_tools", "llm_plan")
    workflow.add_edge("llm_plan", "route_decision")
    workflow.add_conditional_edges(
        "route_decision",
        _route_after_decision,
        {
            "execute_readonly_tool": "execute_readonly_tool",
            "create_recommended_action": "create_recommended_action",
            "final_report": "final_report",
            "fail_report": "fail_report",
        },
    )
    workflow.add_edge("execute_readonly_tool", "llm_plan")
    workflow.add_edge("create_recommended_action", END)
    workflow.add_edge("final_report", END)
    workflow.add_edge("fail_report", END)
    return workflow.compile()


def load_tools(state: AgentGraphState) -> AgentGraphState:
    tools = state["tool_client"].definitions()
    next_state = dict(state)
    next_state["tools"] = tools
    next_state["messages"] = [
        {
            "role": "system",
            "content": "You are a single intelligent Tool-use Agent. Return strict AgentDecision JSON only.",
        },
        {"role": "user", "content": state["request"].goal},
    ]
    return next_state


def llm_plan(state: AgentGraphState) -> AgentGraphState:
    next_state = dict(state)
    decision_client = state["decision_client"]
    last_error: str | None = None

    for attempt in range(2):
        raw_decision = decision_client.decide(next_state)
        try:
            decision = AgentDecision.model_validate_json(raw_decision)
            _validate_decision(decision, state.get("tools", []))
            next_state["decision"] = decision
            return _append_step(
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
            last_error = str(exc)

    next_state["error_message"] = f"Invalid AgentDecision JSON: {last_error}"
    return _append_step(
        next_state,
        "llm_plan",
        step_type="LLM_DECISION",
        status="FAILED",
        output={"validated": False},
        error_message=next_state["error_message"],
    )


def route_decision(state: AgentGraphState) -> AgentGraphState:
    return state


def execute_readonly_tool(state: AgentGraphState) -> AgentGraphState:
    decision = state.get("decision")
    if decision is None or decision.tool_name is None:
        next_state = dict(state)
        next_state["error_message"] = "CALL_TOOL decision must include toolName"
        return next_state

    execution = state["tool_client"].execute(decision.tool_name, state["request"])
    raw_output = execution.output if isinstance(execution.output, dict) else {}
    observation = AgentObservation(
        toolName=decision.tool_name,
        success=execution.success,
        output=raw_output,
        summary=_summarize_observation(raw_output),
        errorMessage=execution.error_message,
        durationMs=execution.duration_ms,
    )
    tool_results = dict(state.get("tool_results", {}))
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

    return _append_step(
        next_state,
        "execute_readonly_tool",
        tool_name=decision.tool_name,
        step_type="TOOL_CALL",
        status="SUCCEEDED" if execution.success else "FAILED",
        input_json=_json({"arguments": decision.arguments}),
        output={
            "raw": raw_output,
            "summaryForLlm": observation.summary,
        },
        duration_ms=execution.duration_ms,
        error_message=execution.error_message,
    )


def create_recommended_action(state: AgentGraphState) -> AgentGraphState:
    decision = state.get("decision")
    if decision is None or decision.tool_name is None:
        next_state = dict(state)
        next_state["error_message"] = "REQUEST_CONFIRMATION decision must include toolName"
        return next_state

    tool = _tool_by_name(state.get("tools", []), decision.tool_name)
    risk_level = decision.risk_level or (tool.risk_level if tool else "MEDIUM")
    action = AgentActionDraft(
        tool_name=decision.tool_name,
        title=f"确认执行 {decision.tool_name}",
        reason=decision.reason,
        risk_level=risk_level,
        requires_confirmation=True,
        action_payload=_json(decision.arguments or {"kbCode": state["request"].kb_code}),
    )
    next_state = dict(state)
    next_state["recommended_actions"] = [*state.get("recommended_actions", []), action]
    next_state["summary"] = f"Agent 已生成待确认动作：{decision.tool_name}"
    return _append_step(
        next_state,
        "create_recommended_action",
        tool_name=decision.tool_name,
        step_type="NODE",
        status="SUCCEEDED",
        output={"recommendedAction": action.model_dump(by_alias=True)},
    )


def final_report(state: AgentGraphState) -> AgentGraphState:
    decision = state.get("decision")
    summary = decision.final_answer if decision and decision.final_answer else "智能 Agent 已完成。"
    next_state = dict(state)
    next_state["summary"] = summary
    return _append_step(next_state, "final_report", output={"summary": summary})


def fail_report(state: AgentGraphState) -> AgentGraphState:
    error_message = state.get("error_message") or "智能 Agent 执行失败。"
    next_state = dict(state)
    next_state["summary"] = f"智能 Agent 执行失败：{error_message}"
    next_state["error_message"] = error_message
    return _append_step(next_state, "fail_report", status="FAILED", output={"summary": next_state["summary"]})


def _route_after_decision(state: AgentGraphState) -> str:
    if state.get("error_message"):
        return "fail_report"
    if int(state.get("tool_call_count") or 0) >= 6:
        state["error_message"] = "Exceeded max tool call count: 6"
        return "fail_report"
    decision = state.get("decision")
    if decision is None:
        state["error_message"] = "Missing AgentDecision"
        return "fail_report"
    if decision.action == "FINAL_ANSWER":
        return "final_report"
    if decision.action == "REQUEST_CONFIRMATION":
        return "create_recommended_action"
    if decision.action == "CALL_TOOL":
        tool = _tool_by_name(state.get("tools", []), decision.tool_name or "")
        if tool is None:
            state["error_message"] = f"Unknown toolName: {decision.tool_name}"
            return "fail_report"
        if _requires_confirmation(tool):
            return "create_recommended_action"
        return "execute_readonly_tool"
    state["error_message"] = f"Unsupported AgentDecision action: {decision.action}"
    return "fail_report"


def parse_goal(state: AgentGraphState) -> AgentGraphState:
    request = state["request"]
    output = {
        "goal": request.goal,
        "question": request.question,
        "runMode": request.run_mode,
        "route": "READINESS_DIAGNOSIS",
    }
    return _append_step(state, "parse_goal", output=output)


def system_health_check(state: AgentGraphState) -> AgentGraphState:
    return _execute_tool_node(state, "system_health_check", "system.health.check")


def kb_readiness_check(state: AgentGraphState) -> AgentGraphState:
    return _execute_tool_node(state, "kb_readiness_check", "kb.readiness.check")


def documents_status_scan(state: AgentGraphState) -> AgentGraphState:
    return _execute_tool_node(state, "documents_status_scan", "documents.status.scan")


def indexing_tasks_scan(state: AgentGraphState) -> AgentGraphState:
    return _execute_tool_node(state, "indexing_tasks_scan", "indexing.tasks.scan")


def qa_retrieve_probe(state: AgentGraphState) -> AgentGraphState:
    if state.get("error_message"):
        return _execute_tool_node(state, "qa_retrieve_probe", "qa.retrieve.probe")
    question = (state["request"].question or "").strip()
    if not question:
        return _append_step(
            state,
            "qa_retrieve_probe",
            tool_name="qa.retrieve.probe",
            step_type="TOOL_CALL",
            status="SKIPPED",
            output={"reason": "question is empty"},
        )
    return _execute_tool_node(state, "qa_retrieve_probe", "qa.retrieve.probe")


def diagnose(state: AgentGraphState) -> AgentGraphState:
    health = _tool_output(state, "system.health.check")
    readiness = _tool_output(state, "kb.readiness.check")
    indexing_tasks = _tool_output(state, "indexing.tasks.scan")
    retrieve_probe = _tool_output(state, "qa.retrieve.probe")
    failed_tasks = _failed_tasks(indexing_tasks)
    retrieve_signals = _retrieve_signals(retrieve_probe)

    if state.get("error_message"):
        diagnosis = {
            "primaryCause": "TOOL_EXECUTION_FAILED",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": False,
        }
    elif health.get("status") != "UP":
        diagnosis = {
            "primaryCause": "SYSTEM_HEALTH_UNAVAILABLE",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": False,
        }
    elif failed_tasks:
        first_failed_task = failed_tasks[0]
        diagnosis = {
            "primaryCause": "FAILED_INDEXING_TASK",
            "reembedRequired": readiness.get("reembedRequired") is True,
            "failedIndexingTaskFound": True,
            "failedTask": first_failed_task,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "nextStep": "RETRY_FAILED_INDEXING_TASK",
            "retrieveSignals": retrieve_signals,
        }
    elif readiness.get("reembedRequired") is True:
        diagnosis = {
            "primaryCause": "REEMBED_REQUIRED",
            "reembedRequired": True,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "nextStep": readiness.get("nextStep"),
            "retrieveSignals": retrieve_signals,
        }
    elif retrieve_signals.get("denseEmpty") and retrieve_signals.get("hybridEmpty"):
        diagnosis = {
            "primaryCause": "RETRIEVAL_NO_HITS",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "retrieveSignals": retrieve_signals,
        }
    elif retrieve_signals.get("keywordZeroHit"):
        diagnosis = {
            "primaryCause": "RETRIEVAL_KEYWORD_ZERO_HIT",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "retrieveSignals": retrieve_signals,
        }
    else:
        diagnosis = {
            "primaryCause": "NO_BLOCKING_ISSUE_FOUND",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "nextStep": readiness.get("nextStep"),
            "retrieveSignals": retrieve_signals,
        }

    next_state = dict(state)
    next_state["diagnosis"] = diagnosis
    return _append_step(next_state, "diagnose", output=diagnosis)


def recommend_actions(state: AgentGraphState) -> AgentGraphState:
    diagnosis = state.get("diagnosis", {})
    actions = list(state.get("recommended_actions", []))
    output: dict[str, Any] = {"recommendedActionCount": 0}

    if (
        state["request"].run_mode == "DIAGNOSE_AND_RECOMMEND"
        and diagnosis.get("primaryCause") == "FAILED_INDEXING_TASK"
    ):
        failed_task = diagnosis.get("failedTask", {})
        action = AgentActionDraft(
            tool_name="document.indexing_task.retry",
            title="重试失败索引任务",
            reason="发现 FAILED indexing task，需要人工确认后由 Java 重试该索引任务。",
            risk_level="MEDIUM",
            requires_confirmation=True,
            action_payload=_json(
                {
                    "kbCode": state["request"].kb_code,
                    "taskId": failed_task.get("taskId"),
                    "documentCode": failed_task.get("documentCode"),
                }
            ),
        )
        actions.append(action)
        output = {
            "recommendedActionCount": 1,
            "toolName": action.tool_name,
            "requiresConfirmation": action.requires_confirmation,
        }
    elif (
        state["request"].run_mode == "DIAGNOSE_AND_RECOMMEND"
        and diagnosis.get("primaryCause") == "REEMBED_REQUIRED"
    ):
        action = AgentActionDraft(
            tool_name="embedding.rebuild.submit",
            title="提交知识库重嵌入任务",
            reason="readiness 显示 embedding 配置变化后尚未完成重嵌入，当前知识库不可问答。",
            risk_level="MEDIUM",
            requires_confirmation=True,
            action_payload=_json({"kbCode": state["request"].kb_code}),
        )
        actions.append(action)
        output = {
            "recommendedActionCount": 1,
            "toolName": action.tool_name,
            "requiresConfirmation": action.requires_confirmation,
        }

    next_state = dict(state)
    next_state["recommended_actions"] = actions
    return _append_step(next_state, "recommend_actions", output=output)


def generate_report(state: AgentGraphState) -> AgentGraphState:
    diagnosis = state.get("diagnosis", {})
    primary_cause = diagnosis.get("primaryCause")

    if state.get("error_message"):
        summary = f"Agent 诊断失败：{state['error_message']}"
    elif primary_cause == "FAILED_INDEXING_TASK":
        summary = "知识库存在失败的索引任务，需要人工确认后重试失败任务。"
    elif primary_cause == "REEMBED_REQUIRED":
        summary = "知识库当前不可问答，主要原因是 embedding 配置变化后尚未完成重嵌入。"
    elif primary_cause == "SYSTEM_HEALTH_UNAVAILABLE":
        summary = "系统健康检查异常，需要先处理基础依赖或模型服务可用性。"
    elif primary_cause == "RETRIEVAL_NO_HITS":
        summary = "Dense 和 Hybrid 检索均无命中，需要检查文档 chunk、embedding 或问题表述。"
    elif primary_cause == "RETRIEVAL_KEYWORD_ZERO_HIT":
        summary = "Dense 检索有结果，但 Hybrid 的 keyword 分支没有贡献命中，关键词召回收益有限。"
    else:
        summary = "当前未发现阻断问答的 readiness 问题。"

    next_state = dict(state)
    next_state["summary"] = summary
    return _append_step(next_state, "generate_report", output={"summary": summary})


def _execute_tool_node(
    state: AgentGraphState,
    node_name: str,
    tool_name: str,
) -> AgentGraphState:
    if state.get("error_message"):
        return _append_step(
            state,
            node_name,
            tool_name=tool_name,
            step_type="TOOL_CALL",
            status="SKIPPED",
            output={"reason": "previous step failed"},
        )

    execution = state["tool_client"].execute(tool_name, state["request"])
    tool_results = dict(state.get("tool_results", {}))
    tool_results[tool_name] = {
        "success": execution.success,
        "output": execution.output,
        "errorMessage": execution.error_message,
        "durationMs": execution.duration_ms,
    }

    next_state = dict(state)
    next_state["tool_results"] = tool_results
    if not execution.success:
        next_state["error_message"] = execution.error_message or f"{tool_name} failed"

    return _append_step(
        next_state,
        node_name,
        tool_name=tool_name,
        step_type="TOOL_CALL",
        status="SUCCEEDED" if execution.success else "FAILED",
        output=execution.output,
        duration_ms=execution.duration_ms,
        error_message=execution.error_message,
    )


def _append_step(
    state: AgentGraphState,
    node_name: str,
    *,
    tool_name: str | None = None,
    step_type: str = "NODE",
    status: str = "SUCCEEDED",
    input_json: str | None = None,
    output: dict[str, Any] | None = None,
    duration_ms: int | None = None,
    error_message: str | None = None,
) -> AgentGraphState:
    next_state = dict(state)
    steps = list(next_state.get("steps", []))
    steps.append(
        AgentStepResult(
            node_name=node_name,
            tool_name=tool_name,
            step_type=step_type,
            status=status,
            input_json=input_json,
            output_json=_json(output or {}),
            duration_ms=duration_ms,
            error_message=error_message,
        )
    )
    next_state["steps"] = steps
    return next_state


def _tool_output(state: AgentGraphState, tool_name: str) -> dict[str, Any]:
    result = state.get("tool_results", {}).get(tool_name, {})
    output = result.get("output")
    return output if isinstance(output, dict) else {}


def _failed_tasks(indexing_tasks: dict[str, Any]) -> list[dict[str, Any]]:
    failed_tasks = indexing_tasks.get("failedTasks", [])
    return failed_tasks if isinstance(failed_tasks, list) else []


def _retrieve_signals(retrieve_probe: dict[str, Any]) -> dict[str, Any]:
    signals = retrieve_probe.get("signals", {})
    return signals if isinstance(signals, dict) else {}


def _validate_decision(decision: AgentDecision, tools: list[AgentToolDefinition]) -> None:
    if decision.action == "FINAL_ANSWER":
        if not decision.final_answer:
            raise ValueError("FINAL_ANSWER requires finalAnswer")
        return

    if not decision.tool_name:
        raise ValueError(f"{decision.action} requires toolName")

    tool = _tool_by_name(tools, decision.tool_name)
    if tool is None:
        raise ValueError(f"Unknown toolName: {decision.tool_name}")

    _validate_arguments(decision.arguments, tool.input_schema)


def _validate_arguments(arguments: dict[str, Any], schema: dict[str, Any]) -> None:
    required = schema.get("required", [])
    if isinstance(required, list):
        for name in required:
            if isinstance(name, str) and name not in arguments:
                raise ValueError(f"Missing required argument: {name}")
    properties = schema.get("properties", {})
    if not isinstance(properties, dict):
        return
    for name, value in arguments.items():
        spec = properties.get(name)
        if not isinstance(spec, dict):
            continue
        expected_type = spec.get("type")
        if expected_type == "string" and not isinstance(value, str):
            raise ValueError(f"Argument {name} must be string")
        if expected_type in {"integer", "number"} and not isinstance(value, (int, float)):
            raise ValueError(f"Argument {name} must be number")
        if expected_type == "boolean" and not isinstance(value, bool):
            raise ValueError(f"Argument {name} must be boolean")


def _tool_by_name(tools: list[AgentToolDefinition], tool_name: str) -> AgentToolDefinition | None:
    return next((tool for tool in tools if tool.name == tool_name), None)


def _requires_confirmation(tool: AgentToolDefinition) -> bool:
    return (
        tool.requires_confirmation
        or tool.execution_mode != "READ_ONLY"
        or tool.risk_level in {"MEDIUM", "HIGH"}
    )


def _observation_output(state: AgentGraphState, tool_name: str) -> dict[str, Any]:
    for observation in state.get("observations", []):
        if observation.tool_name == tool_name:
            return observation.output or {}
    return {}


def _summarize_observation(output: dict[str, Any]) -> dict[str, Any]:
    summary: dict[str, Any] = {}
    for key in [
        "status",
        "kbCode",
        "questionAnsweringReady",
        "reembedRequired",
        "reembedInProgress",
        "nextStep",
        "statusCounts",
        "signals",
        "summary",
        "command",
        "mode",
    ]:
        if key in output:
            summary[key] = output[key]
    for key in ["failedTasks", "failedDocuments", "sources"]:
        value = output.get(key)
        if isinstance(value, list):
            summary[key] = value[:3]
    return summary or {"keys": sorted(output.keys())[:10]}


def _decision_json(
    action: str,
    tool_name: str | None,
    reason: str,
    *,
    arguments: dict[str, Any] | None = None,
    final_answer: str | None = None,
    risk_level: str | None = "LOW",
) -> str:
    return _json(
        {
            "action": action,
            "toolName": tool_name,
            "arguments": arguments or {},
            "reason": reason,
            "finalAnswer": final_answer,
            "riskLevel": risk_level,
        }
    )


def _json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
