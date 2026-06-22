import json

import httpx
from fastapi.testclient import TestClient

from app.agent.runtime import AgentRuntime, get_agent_runtime
from app.agent.state import AgentRuntimeRequest
from app.agent.tools import AgentToolExecution, JavaAgentToolClient
from app.core.config import Settings
from app.main import create_app


def build_client(runtime: AgentRuntime | None = None) -> TestClient:
    app = create_app()
    if runtime is not None:
        app.dependency_overrides[get_agent_runtime] = lambda: runtime
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


def test_intelligent_agent_blocks_write_tool_as_recommended_action():
    runtime = AgentRuntime()
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


def test_intelligent_agent_uses_fake_mcp_and_readonly_cli_before_final_answer():
    runtime = AgentRuntime()
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查当前项目状态，并结合 git 状态给出诊断",
        runMode="INTELLIGENT_TOOL_AGENT",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert response.recommended_actions == []
    tool_steps = [(step.node_name, step.tool_name) for step in response.steps if step.step_type == "TOOL_CALL"]
    assert ("execute_readonly_tool", "mcp.repo.status.inspect") in tool_steps
    assert ("execute_readonly_tool", "cli.git.status") in tool_steps
    assert "未执行任何写操作" in response.summary


def test_intelligent_agent_fails_after_invalid_json_retry():
    runtime = AgentRuntime(decision_client=ScriptedDecisionClient(["not-json", "{bad-json"]))
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


def test_java_agent_tool_client_calls_java_internal_tool_api():
    captured_request: httpx.Request | None = None

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal captured_request
        captured_request = request
        return httpx.Response(
            200,
            json={
                "code": "SUCCESS",
                "message": "OK",
                "data": {
                    "toolName": "kb.readiness.check",
                    "success": True,
                    "outputJson": '{"questionAnsweringReady":true}',
                    "errorMessage": None,
                    "durationMs": 7,
                },
                "requestId": "REQ-test",
                "timestamp": "2026-06-16T00:00:00Z",
            },
        )

    client = JavaAgentToolClient(
        Settings(
            java_agent_tool_base_url="http://java-backend",
            java_agent_tool_token="token-1",
        ),
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
    )

    execution = client.execute(
        "kb.readiness.check",
        AgentRuntimeRequest(
            runCode="AR-test",
            kbCode="day20-cn-kb",
            goal="诊断这个知识库为什么不能问答",
            question="第二百三十八条是什么",
        ),
    )

    assert execution.success is True
    assert execution.output == {"questionAnsweringReady": True}
    assert execution.duration_ms == 7
    assert captured_request is not None
    assert str(captured_request.url) == "http://java-backend/api/internal/agent/tools/kb.readiness.check/execute"
    assert captured_request.headers["X-Agent-Tool-Token"] == "token-1"
    assert captured_request.headers["X-Request-Id"] == "AR-test"
    assert json.loads(captured_request.content) == {
        "runCode": "AR-test",
        "kbCode": "day20-cn-kb",
        "question": "第二百三十八条是什么",
        "operator": "agent-runtime",
        "attributes": {},
    }


def test_java_agent_tool_client_returns_failed_execution_on_java_error():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            400,
            json={
                "code": "BUSINESS_ERROR",
                "message": "Only READ_ONLY agent tools can be executed by Agent Runtime",
            },
        )

    client = JavaAgentToolClient(
        Settings(java_agent_tool_base_url="http://java-backend"),
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
    )

    execution = client.execute(
        "document.indexing_task.retry",
        AgentRuntimeRequest(
            runCode="AR-test",
            kbCode="day20-cn-kb",
            goal="检查索引异常",
        ),
    )

    assert execution.success is False
    assert "BUSINESS_ERROR" in execution.error_message
    assert "Only READ_ONLY" in execution.error_message


def test_java_agent_tool_client_ignores_environment_proxy(monkeypatch):
    captured_kwargs: dict[str, object] = {}

    class CapturingClient:
        def __init__(self, **kwargs):
            captured_kwargs.update(kwargs)

    monkeypatch.setattr("app.agent.tools.httpx.Client", CapturingClient)

    JavaAgentToolClient(Settings(java_agent_tool_base_url="http://java-backend"))

    assert captured_kwargs["trust_env"] is False


class FailingToolClient:
    def execute(self, tool_name: str, request: AgentRuntimeRequest) -> AgentToolExecution:
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


class ScriptedDecisionClient:
    def __init__(self, decisions: list[str]) -> None:
        self._decisions = list(decisions)

    def decide(self, state) -> str:
        if self._decisions:
            return self._decisions.pop(0)
        return json.dumps(
            {
                "action": "FINAL_ANSWER",
                "toolName": None,
                "arguments": {},
                "reason": "fallback",
                "finalAnswer": "fallback",
                "riskLevel": None,
            }
        )
