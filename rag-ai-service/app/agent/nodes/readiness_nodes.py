from __future__ import annotations

from typing import Any

from app.agent.diagnostics.readiness import failed_tasks, retrieve_signals, tool_output
from app.agent.graphs.state import AgentGraphState
from app.agent.graphs.steps import append_step, execute_tool_node, to_json
from app.agent.state import AgentActionDraft


def parse_goal(state: AgentGraphState) -> AgentGraphState:
    """记录本次固定诊断图的入口信息。"""
    request = state["request"]
    output = {
        "goal": request.goal,
        "question": request.question,
        "runMode": request.run_mode,
        "route": "READINESS_DIAGNOSIS",
    }
    return append_step(state, "parse_goal", output=output)


def system_health_check(state: AgentGraphState) -> AgentGraphState:
    """调用系统健康检查工具。"""
    return execute_tool_node(state, "system_health_check", "system.health.check")


def kb_readiness_check(state: AgentGraphState) -> AgentGraphState:
    """调用知识库 readiness 检查工具。"""
    return execute_tool_node(state, "kb_readiness_check", "kb.readiness.check")


def documents_status_scan(state: AgentGraphState) -> AgentGraphState:
    """扫描知识库文档状态。"""
    return execute_tool_node(state, "documents_status_scan", "documents.status.scan")


def indexing_tasks_scan(state: AgentGraphState) -> AgentGraphState:
    """扫描索引任务状态。"""
    return execute_tool_node(state, "indexing_tasks_scan", "indexing.tasks.scan")


def qa_retrieve_probe(state: AgentGraphState) -> AgentGraphState:
    """在有问题文本时执行检索探测，没有 question 时保留 SKIPPED 轨迹。"""
    if state.get("error_message"):
        return execute_tool_node(state, "qa_retrieve_probe", "qa.retrieve.probe")
    question = (state["request"].question or "").strip()
    if not question:
        # question 为空不是失败，固定图仍需要记录该节点被有意跳过。
        return append_step(
            state,
            "qa_retrieve_probe",
            tool_name="qa.retrieve.probe",
            step_type="TOOL_CALL",
            status="SKIPPED",
            output={"reason": "question is empty"},
        )
    return execute_tool_node(state, "qa_retrieve_probe", "qa.retrieve.probe")


def diagnose(state: AgentGraphState) -> AgentGraphState:
    """汇总前面工具输出，按固定优先级生成诊断结论。"""
    health = tool_output(state, "system.health.check")
    readiness = tool_output(state, "kb.readiness.check")
    indexing_tasks = tool_output(state, "indexing.tasks.scan")
    retrieve_probe = tool_output(state, "qa.retrieve.probe")
    failed_task_items = failed_tasks(indexing_tasks)
    retrieve_signal_values = retrieve_signals(retrieve_probe)

    if state.get("error_message"):
        # 工具执行失败优先级最高，避免后续用不完整数据误判业务原因。
        diagnosis = {
            "primaryCause": "TOOL_EXECUTION_FAILED",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": False,
        }
    elif health.get("status") != "UP":
        # 基础依赖不可用时，先提示处理系统健康，而不是继续推业务动作。
        diagnosis = {
            "primaryCause": "SYSTEM_HEALTH_UNAVAILABLE",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": False,
        }
    elif failed_task_items:
        # 失败索引任务比 reembed 更具体，优先推荐 retry 该任务。
        first_failed_task = failed_task_items[0]
        diagnosis = {
            "primaryCause": "FAILED_INDEXING_TASK",
            "reembedRequired": readiness.get("reembedRequired") is True,
            "failedIndexingTaskFound": True,
            "failedTask": first_failed_task,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "nextStep": "RETRY_FAILED_INDEXING_TASK",
            "retrieveSignals": retrieve_signal_values,
        }
    elif readiness.get("reembedRequired") is True:
        # readiness 明确要求重嵌入时，只生成待确认动作，不由 Python 直接执行。
        diagnosis = {
            "primaryCause": "REEMBED_REQUIRED",
            "reembedRequired": True,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "nextStep": readiness.get("nextStep"),
            "retrieveSignals": retrieve_signal_values,
        }
    elif retrieve_signal_values.get("denseEmpty") and retrieve_signal_values.get("hybridEmpty"):
        # Dense/Hybrid 都无命中时，定位为检索侧无召回。
        diagnosis = {
            "primaryCause": "RETRIEVAL_NO_HITS",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "retrieveSignals": retrieve_signal_values,
        }
    elif retrieve_signal_values.get("keywordZeroHit"):
        # Dense 有结果但 keyword 无贡献时，单独标记 Hybrid keyword 分支问题。
        diagnosis = {
            "primaryCause": "RETRIEVAL_KEYWORD_ZERO_HIT",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "retrieveSignals": retrieve_signal_values,
        }
    else:
        # 没有命中已知阻断原因时，返回无阻断问题，保持诊断链路收敛。
        diagnosis = {
            "primaryCause": "NO_BLOCKING_ISSUE_FOUND",
            "reembedRequired": False,
            "failedIndexingTaskFound": False,
            "questionAnsweringReady": readiness.get("questionAnsweringReady") is True,
            "nextStep": readiness.get("nextStep"),
            "retrieveSignals": retrieve_signal_values,
        }

    next_state = dict(state)
    next_state["diagnosis"] = diagnosis
    return append_step(next_state, "diagnose", output=diagnosis)


def recommend_actions(state: AgentGraphState) -> AgentGraphState:
    """根据诊断结论生成推荐动作草案。"""
    diagnosis = state.get("diagnosis", {})
    actions = list(state.get("recommended_actions", []))
    output: dict[str, Any] = {"recommendedActionCount": 0}

    if (
        state["request"].run_mode == "DIAGNOSE_AND_RECOMMEND"
        and diagnosis.get("primaryCause") == "FAILED_INDEXING_TASK"
    ):
        # Python 只返回 action 草案，Java 负责生成 actionCode、落库和确认后执行。
        failed_task = diagnosis.get("failedTask", {})
        action = AgentActionDraft(
            tool_name="document.indexing_task.retry",
            title="重试失败索引任务",
            reason="发现 FAILED indexing task，需要人工确认后由 Java 重试该索引任务。",
            risk_level="MEDIUM",
            requires_confirmation=True,
            action_payload=to_json(
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
        # 重嵌入是 MEDIUM 风险写操作，必须走 human-in-the-loop。
        action = AgentActionDraft(
            tool_name="embedding.rebuild.submit",
            title="提交知识库重嵌入任务",
            reason="readiness 显示 embedding 配置变化后尚未完成重嵌入，当前知识库不可问答。",
            risk_level="MEDIUM",
            requires_confirmation=True,
            action_payload=to_json({"kbCode": state["request"].kb_code}),
        )
        actions.append(action)
        output = {
            "recommendedActionCount": 1,
            "toolName": action.tool_name,
            "requiresConfirmation": action.requires_confirmation,
        }

    next_state = dict(state)
    next_state["recommended_actions"] = actions
    return append_step(next_state, "recommend_actions", output=output)


def generate_report(state: AgentGraphState) -> AgentGraphState:
    """把结构化诊断结论转换为面向前端展示的 summary。"""
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
    return append_step(next_state, "generate_report", output={"summary": summary})
