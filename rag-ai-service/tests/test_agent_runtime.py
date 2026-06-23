import json

import httpx
from fastapi.testclient import TestClient

from app.agent.planners.llm import LlmAgentDecisionClient
from app.agent.runtime import AgentRuntime, _default_decision_client, _default_tool_client, get_agent_runtime
from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.state import AgentDecision
from app.agent.tools import AgentToolExecution, McpAgentToolClient
from app.core.config import Settings
from app.main import create_app


def build_client(runtime: AgentRuntime | None = None) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_agent_runtime] = lambda: runtime or AgentRuntime(tool_client=TestToolClient())
    return TestClient(app)


def test_agent_run_returns_minimal_langgraph_trace_and_reembed_action():
    client = build_client()

    response = client.post(
        "/v1/agent/runs",
        headers={"X-Request-Id": "REQ-AGENT-1"},
        json={
            "runCode": "AR-test",
            "kbCode": "day20-cn-kb",
            "goal": "诊断这个知识库为什么不能问答",
            "runMode": "DIAGNOSE_AND_RECOMMEND",
        },
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "REQ-AGENT-1"
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert "stepCode" not in str(body)
    assert "actionCode" not in str(body)
    assert [step["nodeName"] for step in body["steps"]] == [
        "parse_goal",
        "system_health_check",
        "kb_readiness_check",
        "documents_status_scan",
        "indexing_tasks_scan",
        "qa_retrieve_probe",
        "diagnose",
        "recommend_actions",
        "generate_report",
    ]
    probe_step = next(step for step in body["steps"] if step["nodeName"] == "qa_retrieve_probe")
    assert probe_step["status"] == "SKIPPED"
    assert body["recommendedActions"][0]["toolName"] == "embedding.rebuild.submit"
    assert body["recommendedActions"][0]["riskLevel"] == "MEDIUM"
    assert body["recommendedActions"][0]["requiresConfirmation"] is True
    assert body["recommendedActions"][0]["actionPayload"] == '{"kbCode":"day20-cn-kb"}'


def test_agent_run_returns_retry_action_for_failed_indexing_task():
    client = build_client()

    response = client.post(
        "/v1/agent/runs",
        json={
            "runCode": "AR-test",
            "kbCode": "day20-cn-kb",
            "goal": "检查这个知识库有没有索引异常",
            "runMode": "DIAGNOSE_AND_RECOMMEND",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert body["recommendedActions"][0]["toolName"] == "document.indexing_task.retry"
    assert body["recommendedActions"][0]["riskLevel"] == "MEDIUM"
    assert body["recommendedActions"][0]["requiresConfirmation"] is True
    assert "actionCode" not in str(body)
    assert '"taskId":1001' in body["recommendedActions"][0]["actionPayload"]
    assert '"documentCode":"DOC-failed-demo"' in body["recommendedActions"][0]["actionPayload"]
    assert "FAILED indexing task" in body["summary"] or "失败的索引任务" in body["summary"]


def test_agent_run_executes_retrieve_probe_when_question_is_present():
    client = build_client()

    response = client.post(
        "/v1/agent/runs",
        json={
            "runCode": "AR-test",
            "kbCode": "day20-cn-kb",
            "goal": "检查这个知识库的检索效果",
            "question": "关键词零命中时 Hybrid 有没有收益",
            "runMode": "DIAGNOSE_AND_RECOMMEND",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert body["recommendedActions"] == []
    probe_step = next(step for step in body["steps"] if step["nodeName"] == "qa_retrieve_probe")
    assert probe_step["toolName"] == "qa.retrieve.probe"
    assert probe_step["status"] == "SUCCEEDED"
    output = json.loads(probe_step["outputJson"])
    assert output["dense"]["retrievalMode"] == "DENSE"
    assert output["hybrid"]["retrievalMode"] == "HYBRID"
    assert output["signals"]["keywordZeroHit"] is True
    assert "keyword 分支没有贡献命中" in body["summary"]
    assert "stepCode" not in str(body)
    assert "actionCode" not in str(body)


def test_agent_run_diagnose_only_skips_recommended_write_actions():
    client = build_client()

    response = client.post(
        "/v1/agent/runs",
        json={
            "runCode": "AR-test",
            "kbCode": "day20-cn-kb",
            "goal": "诊断这个知识库为什么不能问答",
            "runMode": "DIAGNOSE_ONLY",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert body["recommendedActions"] == []


def test_agent_runtime_returns_failed_when_tool_call_fails():
    runtime = AgentRuntime(tool_client=FailingToolClient())
    request = AgentRuntimeRequest(
        run_code="AR-test",
        kb_code="day20-cn-kb",
        goal="诊断这个知识库为什么不能问答",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert response.error_message == "backend unavailable"
    assert any(step.status == "FAILED" for step in response.steps)


def test_default_planner_uses_llm_client(monkeypatch):
    monkeypatch.setattr("app.agent.runtime.get_settings", lambda: Settings())

    decision_client = _default_decision_client()

    assert isinstance(decision_client, LlmAgentDecisionClient)


def test_llm_planner_parses_call_tool_content():
    planner = LlmAgentDecisionClient(
        Settings(),
        provider_client=FakeProviderClient([
            {
                "action": "CALL_TOOL",
                "toolName": "kb.readiness.check",
                "arguments": {"kbCode": "day20-cn-kb"},
                "reason": "check readiness",
                "finalAnswer": None,
                "riskLevel": "LOW",
            }
        ]),
    )
    state = _minimal_agent_state([AgentToolDefinition(toolName="kb.readiness.check")])

    raw = planner.decide(state)

    assert json.loads(raw)["toolName"] == "kb.readiness.check"


def test_llm_planner_parses_final_answer_content():
    planner = LlmAgentDecisionClient(
        Settings(),
        provider_client=FakeProviderClient([
            {
                "action": "FINAL_ANSWER",
                "toolName": None,
                "arguments": {},
                "reason": "enough information",
                "finalAnswer": "可以问答。",
                "riskLevel": None,
            }
        ]),
    )

    raw = planner.decide(_minimal_agent_state([]))

    assert json.loads(raw)["finalAnswer"] == "可以问答。"


def test_llm_planner_prompt_contains_agent_context_without_timeline_leakage():
    provider = FakeProviderClient([
        {
            "action": "FINAL_ANSWER",
            "toolName": None,
            "arguments": {},
            "reason": "enough information",
            "finalAnswer": "完成",
            "riskLevel": None,
        }
    ])
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=_llm_decision_client([], provider),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查知识库",
        question="第二百三十八条是什么",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert len(provider.requests) == 1
    payload = provider.requests[0]
    context = json.loads(payload["messages"][1]["content"])
    assert context["goal"] == "检查知识库"
    assert context["question"] == "第二百三十八条是什么"
    assert context["kbCode"] == "day20-cn-kb"
    assert context["toolCallCount"] == 0
    assert any(tool["toolName"] == "kb.readiness.check" for tool in context["visibleTools"])
    llm_step = next(step for step in response.steps if step.step_type == "LLM_DECISION")
    assert "messages" not in llm_step.output_json
    assert "visibleTools" not in llm_step.output_json
    assert "raw" not in llm_step.output_json.lower()


def test_intelligent_agent_blocks_write_tool_as_recommended_action():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=_llm_decision_client([
            {
                "action": "CALL_TOOL",
                "toolName": "kb.readiness.check",
                "arguments": {"kbCode": "day20-cn-kb"},
                "reason": "check readiness",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "REQUEST_CONFIRMATION",
                "toolName": "embedding.rebuild.submit",
                "arguments": {"kbCode": "day20-cn-kb"},
                "reason": "readiness requires reembedding",
                "finalAnswer": None,
                "riskLevel": "MEDIUM",
            },
        ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="诊断这个知识库为什么不能问答",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert response.recommended_actions[0].tool_name == "embedding.rebuild.submit"
    assert response.recommended_actions[0].risk_level == "MEDIUM"
    assert response.recommended_actions[0].requires_confirmation is True
    assert [step.step_type for step in response.steps].count("LLM_DECISION") >= 2
    assert any(step.node_name == "execute_readonly_tool" and step.tool_name == "kb.readiness.check" for step in response.steps)
    assert any(step.node_name == "create_recommended_action" for step in response.steps)


def test_intelligent_agent_uses_mcp_readonly_tools_before_final_answer():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=_llm_decision_client([
            {
                "action": "CALL_TOOL",
                "toolName": "kb.readiness.check",
                "arguments": {"kbCode": "day20-cn-kb"},
                "reason": "check readiness",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "CALL_TOOL",
                "toolName": "retrieval.config.inspect",
                "arguments": {"kbCode": "day20-cn-kb"},
                "reason": "inspect retrieval config",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "FINAL_ANSWER",
                "toolName": None,
                "arguments": {},
                "reason": "enough observations",
                "finalAnswer": "已完成 readiness 和检索配置检查，未执行任何写操作。",
                "riskLevel": None,
            },
        ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查知识库 readiness 和检索配置后给出诊断",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert response.recommended_actions == []
    tool_steps = [step.tool_name for step in response.steps if step.step_type == "TOOL_CALL"]
    assert "kb.readiness.check" in tool_steps
    assert "retrieval.config.inspect" in tool_steps
    assert "未执行任何写操作" in response.summary


def test_intelligent_agent_fails_after_invalid_json_retry():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=_llm_decision_client_raw(["not-json", "{bad-json"]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查当前项目状态",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Invalid AgentDecision JSON" in response.error_message
    assert any(step.step_type == "LLM_DECISION" and step.status == "FAILED" for step in response.steps)


def test_intelligent_agent_rejects_unknown_tool_name():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=_llm_decision_client([
            {
                "action": "CALL_TOOL",
                "toolName": "missing.tool",
                "arguments": {},
                "reason": "try missing tool",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "CALL_TOOL",
                "toolName": "still.missing",
                "arguments": {},
                "reason": "try missing tool again",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
        ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查未知工具",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Unknown toolName" in response.error_message
    assert not any(step.step_type == "TOOL_CALL" for step in response.steps)


def test_llm_invalid_json_retries_once_and_fails_without_raw_response():
    provider = FakeRawProviderClient(["not-json", "{bad-json"])
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=LlmAgentDecisionClient(Settings(), provider_client=provider),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查当前项目状态",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Invalid AgentDecision JSON" in response.error_message
    llm_step = next(step for step in response.steps if step.step_type == "LLM_DECISION" and step.status == "FAILED")
    output = json.loads(llm_step.output_json)
    assert output["validated"] is False
    assert "errorSummary" in output
    assert "not-json" not in llm_step.output_json
    assert len(provider.requests) == 2
    retry_context = json.loads(provider.requests[1]["messages"][1]["content"])
    assert "previousInvalidDecisionError" in retry_context
    assert "retryInstruction" in retry_context
    assert "corrected AgentDecision JSON" in retry_context["retryInstruction"]


def test_llm_unknown_tool_is_blocked_by_validation():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=LlmAgentDecisionClient(
            Settings(),
            provider_client=FakeProviderClient([
                {
                    "action": "CALL_TOOL",
                    "toolName": "missing.tool",
                    "arguments": {},
                    "reason": "try missing tool",
                    "finalAnswer": None,
                    "riskLevel": "LOW",
                },
                {
                    "action": "CALL_TOOL",
                    "toolName": "still.missing",
                    "arguments": {},
                    "reason": "try missing tool again",
                    "finalAnswer": None,
                    "riskLevel": "LOW",
                },
            ]),
        )
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查未知工具",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Unknown toolName" in response.error_message
    assert not any(step.step_type == "TOOL_CALL" for step in response.steps)


def test_llm_provider_failure_makes_agent_run_failed_without_fallback():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=LlmAgentDecisionClient(Settings(), provider_client=RaisingProviderClient("missing chat provider api key")),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查知识库",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "missing chat provider api key" in response.error_message
    assert response.recommended_actions == []
    assert not any(step.step_type == "TOOL_CALL" for step in response.steps)


def test_intelligent_agent_rejects_arguments_that_do_not_match_schema():
    runtime = AgentRuntime(
        tool_client=SchemaToolClient(),
        decision_client=_llm_decision_client([
            {
                "action": "CALL_TOOL",
                "toolName": "schema.test",
                "arguments": {"count": "not-a-number"},
                "reason": "schema mismatch",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "CALL_TOOL",
                "toolName": "schema.test",
                "arguments": {},
                "reason": "missing required arg",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
        ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查 schema",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Argument count must be number" in response.error_message or "Missing required argument" in response.error_message
    assert not any(step.step_type == "TOOL_CALL" for step in response.steps)


def test_llm_call_tool_for_confirmation_tool_is_not_executed():
    tool_client = CapturingToolClient()
    runtime = AgentRuntime(
        tool_client=tool_client,
        decision_client=LlmAgentDecisionClient(
            Settings(),
            provider_client=FakeProviderClient([
                {
                    "action": "CALL_TOOL",
                    "toolName": "embedding.rebuild.submit",
                    "arguments": {"kbCode": "day20-cn-kb"},
                    "reason": "needs rebuild",
                    "finalAnswer": None,
                    "riskLevel": "MEDIUM",
                },
                {
                    "action": "CALL_TOOL",
                    "toolName": "embedding.rebuild.submit",
                    "arguments": {"kbCode": "day20-cn-kb"},
                    "reason": "needs rebuild",
                    "finalAnswer": None,
                    "riskLevel": "MEDIUM",
                },
            ]),
        ),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="诊断这个知识库为什么不能问答",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Unknown toolName" in response.error_message
    assert response.recommended_actions == []
    assert tool_client.executed == []


def test_execute_readonly_tool_passes_decision_arguments_to_tool_client():
    tool_client = CapturingToolClient()
    runtime = AgentRuntime(
        tool_client=tool_client,
        decision_client=_llm_decision_client([
            {
                "action": "CALL_TOOL",
                "toolName": "qa.retrieve.probe",
                "arguments": {
                    "kbCode": "day20-cn-kb",
                    "question": "改写后的问题",
                    "topK": 3,
                },
                "reason": "probe retrieval",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "FINAL_ANSWER",
                "toolName": None,
                "arguments": {},
                "reason": "done",
                "finalAnswer": "完成",
                "riskLevel": None,
            },
        ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查检索",
        question="原始问题",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert tool_client.executed == [
        (
            "qa.retrieve.probe",
            {"kbCode": "day20-cn-kb", "question": "改写后的问题", "topK": 3},
        )
    ]
    tool_step = next(step for step in response.steps if step.node_name == "execute_readonly_tool")
    step_input = json.loads(tool_step.input_json)
    assert step_input["originalQuestion"] == "原始问题"
    assert step_input["toolQuestion"] == "改写后的问题"


def test_kb_code_mismatch_is_validation_failure():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        decision_client=_llm_decision_client([
            {
                "action": "CALL_TOOL",
                "toolName": "qa.retrieve.probe",
                "arguments": {"kbCode": "other-kb", "question": "Q"},
                "reason": "probe retrieval",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
            {
                "action": "CALL_TOOL",
                "toolName": "qa.retrieve.probe",
                "arguments": {"kbCode": "still-other", "question": "Q"},
                "reason": "probe retrieval",
                "finalAnswer": None,
                "riskLevel": "LOW",
            },
        ])
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查检索",
        question="原始问题",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "kbCode must match request.kbCode" in response.error_message
    assert not any(step.step_type == "TOOL_CALL" for step in response.steps)


def test_intelligent_agent_fails_when_tool_call_count_exceeds_limit():
    repeated_decisions = [
        json.dumps(
            {
                "action": "CALL_TOOL",
                "toolName": "system.health.check",
                "arguments": {},
                "reason": "keep checking",
                "finalAnswer": None,
                "riskLevel": "LOW",
            }
        )
        for _ in range(7)
    ]
    runtime = AgentRuntime(tool_client=TestToolClient(), decision_client=_llm_decision_client_raw(repeated_decisions))
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="循环调用工具",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert response.error_message == "Exceeded max tool call count: 6"
    assert sum(1 for step in response.steps if step.step_type == "TOOL_CALL") == 6


def test_agent_tools_facade_re_exports_mcp_imports():
    from app.agent.tools import AgentToolClient as FacadeAgentToolClient
    from app.agent.tools import AgentToolExecution as FacadeAgentToolExecution
    from app.agent.tools import McpAgentToolClient as FacadeMcpAgentToolClient

    assert FacadeAgentToolClient is not None
    assert FacadeAgentToolExecution is AgentToolExecution
    assert FacadeMcpAgentToolClient is McpAgentToolClient


def test_tool_definition_accepts_json_string_and_plain_text_schema():
    json_schema_tool = AgentToolDefinition(
        toolName="schema.json",
        inputSchema='{"type":"object","required":["kbCode"]}',
        outputSchema="plain text output contract",
    )

    assert json_schema_tool.input_schema["required"] == ["kbCode"]
    assert json_schema_tool.output_schema["description"] == "plain text output contract"


def test_agent_decision_accepts_null_arguments_as_empty_object():
    decision = AgentDecision.model_validate(
        {
            "action": "FINAL_ANSWER",
            "toolName": None,
            "arguments": None,
            "reason": "done",
            "finalAnswer": "完成",
            "riskLevel": None,
        }
    )

    assert decision.arguments == {}


def test_mcp_agent_tool_client_definitions_initializes_and_lists_tools():
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        payload = json.loads(request.content)
        if payload["method"] == "initialize":
            return httpx.Response(
                200,
                headers={"Mcp-Session-Id": "session-1"},
                json={
                    "jsonrpc": "2.0",
                    "id": payload["id"],
                    "result": {
                        "protocolVersion": "2025-06-18",
                        "capabilities": {"tools": {"listChanged": False}},
                        "serverInfo": {"name": "rag-java-mcp-server", "version": "0.1.0"},
                    },
                },
            )
        if payload["method"] == "notifications/initialized":
            return httpx.Response(202)
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": payload["id"],
                "result": {
                    "tools": [
                        {
                            "name": "kb.readiness.check",
                            "title": "kb.readiness.check",
                            "description": "检查 readiness",
                            "inputSchema": {"type": "object", "required": ["kbCode"]},
                            "annotations": {
                                "x-rag.executionMode": "READ_ONLY",
                                "x-rag.maxRiskLevel": "LOW",
                                "x-rag.requiresConfirmation": False,
                            },
                        }
                    ]
                },
            },
        )

    client = McpAgentToolClient(
        Settings(mcp_tool_base_url="http://java-backend"),
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
    )

    definitions = client.definitions()

    assert definitions[0].name == "kb.readiness.check"
    assert definitions[0].source_type == "MCP"
    assert requests[0].headers["Accept"] == "application/json, text/event-stream"
    assert requests[0].headers["Origin"] == "http://127.0.0.1:8001"
    assert requests[1].headers["Mcp-Session-Id"] == "session-1"
    assert requests[1].headers["MCP-Protocol-Version"] == "2025-06-18"


def test_mcp_agent_tool_client_execute_calls_tools_call_and_parses_structured_content():
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        payload = json.loads(request.content)
        if payload["method"] == "initialize":
            return httpx.Response(200, headers={"Mcp-Session-Id": "session-1"}, json={"jsonrpc": "2.0", "id": payload["id"], "result": {"protocolVersion": "2025-06-18"}})
        if payload["method"] == "notifications/initialized":
            return httpx.Response(202)
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": payload["id"],
                "result": {
                    "content": [{"type": "text", "text": "{\"questionAnsweringReady\":true}"}],
                    "structuredContent": {"questionAnsweringReady": True},
                    "isError": False,
                },
            },
        )

    client = McpAgentToolClient(
        Settings(mcp_tool_base_url="http://java-backend"),
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
    )

    execution = client.execute(
        "kb.readiness.check",
        AgentRuntimeRequest(runCode="AR-test", kbCode="day20-cn-kb", goal="诊断", question="原始问题"),
        {"question": "改写问题"},
    )

    assert execution.success is True
    assert execution.output == {"questionAnsweringReady": True}
    call_payload = json.loads(requests[-1].content)
    assert call_payload["method"] == "tools/call"
    assert call_payload["params"]["name"] == "kb.readiness.check"
    assert call_payload["params"]["arguments"] == {
        "runCode": "AR-test",
        "kbCode": "day20-cn-kb",
        "question": "改写问题",
        "operator": "agent-runtime",
        "attributes": {},
    }


def test_mcp_agent_tool_client_reinitializes_on_missing_session():
    call_count = {"initialize": 0, "tools/list": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        if payload["method"] == "initialize":
            call_count["initialize"] += 1
            return httpx.Response(200, headers={"Mcp-Session-Id": f"session-{call_count['initialize']}"}, json={"jsonrpc": "2.0", "id": payload["id"], "result": {"protocolVersion": "2025-06-18"}})
        if payload["method"] == "notifications/initialized":
            return httpx.Response(202)
        call_count["tools/list"] += 1
        if call_count["tools/list"] == 1:
            return httpx.Response(404)
        return httpx.Response(200, json={"jsonrpc": "2.0", "id": payload["id"], "result": {"tools": []}})

    client = McpAgentToolClient(
        Settings(mcp_tool_base_url="http://java-backend"),
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
    )

    assert client.definitions() == []
    assert call_count == {"initialize": 2, "tools/list": 2}


def test_default_tool_client_uses_mcp_client(monkeypatch):
    monkeypatch.setattr("app.agent.runtime.get_settings", lambda: Settings(agent_tool_client="mcp"))

    assert isinstance(_default_tool_client(), McpAgentToolClient)


def test_mcp_agent_tool_client_ignores_environment_proxy(monkeypatch):
    captured_kwargs: dict[str, object] = {}

    class CapturingClient:
        def __init__(self, **kwargs):
            captured_kwargs.update(kwargs)

    monkeypatch.setattr("app.agent.tools.mcp_client.httpx.Client", CapturingClient)

    McpAgentToolClient(Settings(mcp_tool_base_url="http://java-backend"))

    assert captured_kwargs["trust_env"] is False


class FailingToolClient:
    def definitions(self) -> list[AgentToolDefinition]:
        return _test_tool_definitions()

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict | None = None,
    ) -> AgentToolExecution:
        if tool_name == "system.health.check":
            return AgentToolExecution(
                tool_name=tool_name,
                success=False,
                error_message="backend unavailable",
                duration_ms=1,
            )
        return AgentToolExecution(
            tool_name=tool_name,
            success=True,
            output={"questionAnsweringReady": True, "reembedRequired": False},
            duration_ms=1,
        )


class TestToolClient:
    def definitions(self) -> list[AgentToolDefinition]:
        return _test_tool_definitions()

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict | None = None,
    ) -> AgentToolExecution:
        arguments = arguments or {"kbCode": request.kb_code}
        if tool_name == "system.health.check":
            output = {"status": "UP", "serviceName": "rag-backend", "components": []}
        elif tool_name == "kb.readiness.check":
            reembed_required = any(marker in request.goal for marker in ["不能问答", "readiness", "不可问答"])
            output = {
                "kbCode": request.kb_code,
                "questionAnsweringReady": not reembed_required,
                "reembedRequired": reembed_required,
                "reembedInProgress": False,
            }
        elif tool_name == "documents.status.scan":
            output = {"kbCode": request.kb_code, "totalDocumentCount": 1, "statusCounts": {"FAILED": 0}, "failedDocuments": []}
        elif tool_name == "indexing.tasks.scan":
            has_failed = "索引" in request.goal
            output = {
                "kbCode": request.kb_code,
                "scannedTaskCount": 1,
                "statusCounts": {"FAILED": 1 if has_failed else 0},
                "failedTasks": [
                    {"taskId": 1001, "documentCode": "DOC-failed-demo", "errorMessage": "failed"}
                ]
                if has_failed
                else [],
            }
        elif tool_name == "qa.retrieve.probe":
            keyword_zero_hit = "关键词零命中" in (arguments.get("question") or request.question or "")
            output = {
                "question": arguments.get("question"),
                "topK": arguments.get("topK", 5),
                "dense": {"retrievalMode": "DENSE", "hitCount": 1, "denseHitCount": 1, "keywordHitCount": 0},
                "hybrid": {"retrievalMode": "HYBRID", "hitCount": 1, "denseHitCount": 1, "keywordHitCount": 0 if keyword_zero_hit else 1},
                "signals": {"keywordZeroHit": keyword_zero_hit, "hybridNoGain": keyword_zero_hit},
            }
        elif tool_name == "retrieval.config.inspect":
            output = {"defaultMode": "HYBRID", "denseCandidateLimit": 8, "keywordCandidateLimit": 8, "fusionK": 60}
        else:
            return AgentToolExecution(tool_name=tool_name, success=False, error_message=f"Unsupported test tool: {tool_name}")
        return AgentToolExecution(tool_name=tool_name, success=True, output=output, duration_ms=1)


class SchemaToolClient:
    def definitions(self) -> list[AgentToolDefinition]:
        return [
            AgentToolDefinition(
                toolName="schema.test",
                schemaVersion="v2",
                description="schema validation test tool",
                inputSchema={
                    "type": "object",
                    "required": ["count"],
                    "properties": {"count": {"type": "integer"}},
                },
                outputSchema={"type": "object"},
                executionMode="READ_ONLY",
                maxRiskLevel="LOW",
                sourceType="JAVA",
                requiresConfirmation=False,
                timeoutMs=5000,
            )
        ]

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict | None = None,
    ) -> AgentToolExecution:
        return AgentToolExecution(tool_name=tool_name, success=True, output={"ok": True}, duration_ms=1)


class CapturingToolClient:
    def __init__(self) -> None:
        self.executed: list[tuple[str, dict | None]] = []

    def definitions(self) -> list[AgentToolDefinition]:
        return [
            AgentToolDefinition(toolName="qa.retrieve.probe"),
        ]

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict | None = None,
    ) -> AgentToolExecution:
        self.executed.append((tool_name, arguments))
        return AgentToolExecution(
            tool_name=tool_name,
            success=True,
            output={"question": (arguments or {}).get("question"), "topK": (arguments or {}).get("topK")},
            duration_ms=1,
        )


class FakeProviderClient:
    def __init__(self, decisions: list[dict]) -> None:
        self._decisions = list(decisions)
        self.requests: list[dict] = []

    def post_json(self, target, payload, request_id):
        self.requests.append(payload)
        decision = self._decisions.pop(0)
        return {
            "choices": [
                {
                    "message": {
                        "content": json.dumps(decision, ensure_ascii=False),
                    }
                }
            ]
        }


class FakeRawProviderClient:
    def __init__(self, contents: list[str]) -> None:
        self._contents = list(contents)
        self.requests: list[dict] = []

    def post_json(self, target, payload, request_id):
        self.requests.append(payload)
        content = self._contents.pop(0)
        return {"choices": [{"message": {"content": content}}]}


class RaisingProviderClient:
    def __init__(self, message: str) -> None:
        self._message = message

    def post_json(self, target, payload, request_id):
        raise RuntimeError(self._message)


def _llm_decision_client(decisions: list[dict], provider: FakeProviderClient | None = None) -> LlmAgentDecisionClient:
    return LlmAgentDecisionClient(Settings(), provider_client=provider or FakeProviderClient(decisions))


def _llm_decision_client_raw(contents: list[str], provider: FakeRawProviderClient | None = None) -> LlmAgentDecisionClient:
    return LlmAgentDecisionClient(Settings(), provider_client=provider or FakeRawProviderClient(contents))


def _minimal_agent_state(tools: list[AgentToolDefinition]) -> dict:
    return {
        "request": AgentRuntimeRequest(
            runCode="AR-test",
            kbCode="day20-cn-kb",
            goal="检查知识库",
            question="第二百三十八条是什么",
            runMode="INTELLIGENT_TOOL_AGENT",
        ),
        "tools": tools,
        "observations": [],
        "tool_call_count": 0,
        "planner_error_message": None,
    }


def _test_tool_definitions() -> list[AgentToolDefinition]:
    return [
        AgentToolDefinition(toolName="system.health.check"),
        AgentToolDefinition(toolName="kb.readiness.check"),
        AgentToolDefinition(toolName="documents.status.scan"),
        AgentToolDefinition(toolName="indexing.tasks.scan"),
        AgentToolDefinition(
            toolName="qa.retrieve.probe",
            inputSchema={
                "type": "object",
                "required": ["kbCode", "question"],
                "properties": {
                    "kbCode": {"type": "string"},
                    "question": {"type": "string"},
                    "topK": {"type": "integer", "minimum": 1, "maximum": 10},
                },
            },
        ),
        AgentToolDefinition(toolName="retrieval.config.inspect"),
    ]
