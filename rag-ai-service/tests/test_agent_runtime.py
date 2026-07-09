import json

import pytest
from fastapi.testclient import TestClient
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage, ToolMessage

from app.agent.graph import AgentFinalAnswer
from app.agent.runtime import AgentRuntime, _default_tool_client, get_agent_runtime
from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools import AgentToolExecution, McpAgentToolClient
from app.core.config import Settings
from app.main import create_app


def build_client(runtime: AgentRuntime) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_agent_runtime] = lambda: runtime
    return TestClient(app)


def test_agent_run_uses_langgraph_main_path_structured_final_answer():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                _final_answer_message("默认智能图执行完成。"),
            ]),
    )
    client = build_client(runtime)

    response = client.post(
        "/v1/agent/runs",
        headers={"X-Request-Id": "REQ-AGENT-1"},
        json={
            "runCode": "AR-test",
            "kbCode": "day20-cn-kb",
            "goal": "检查这个知识库状态",
        },
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "REQ-AGENT-1"
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert body["summary"] == "默认智能图执行完成。"
    assert [step["nodeName"] for step in body["steps"]] == ["agent_model"]
    assert body["steps"][0]["stepType"] == "LLM_DECISION"
    assert "stepCode" not in str(body)
    assert "actionCode" not in str(body)


def test_agent_stream_maps_langgraph_model_and_tool_nodes_to_runtime_events():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "kb_readiness_check",
                            "args": {"kbCode": "day20-cn-kb"},
                            "id": "call-1",
                        }
                    ],
                ),
                _final_answer_message("检查完成。"),
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-stream-intelligent",
        kbCode="day20-cn-kb",
        goal="诊断这个知识库为什么不能问答",
    )

    events = _runtime_events(runtime.stream_sse(request))

    assert events[0]["type"] == "RUN_STARTED"
    assert [event["type"] for event in events if event["terminal"]] == ["RUN_COMPLETED"]
    assert any(
        event["type"] == "PLANNER_DECISION"
        and event["payload"]["toolCalls"][0]["toolName"] == "kb.readiness.check"
        for event in events
    )
    assert any(
        event["type"] == "TOOL_CALL_COMPLETED" and event["toolName"] == "kb.readiness.check"
        for event in events
    )
    assert any(
        event["type"] == "OBSERVATION_CREATED" and event["toolName"] == "kb.readiness.check"
        for event in events
    )
    step_events = [
        event
        for event in events
        if event["nodeName"] == "execute_readonly_tool"
        and event["type"] in {"STEP_STARTED", "STEP_COMPLETED", "STEP_FAILED"}
    ]
    assert [event["type"] for event in step_events] == ["STEP_STARTED", "STEP_COMPLETED"]
    assert len({event["nodeInvocationId"] for event in step_events}) == 1


def test_langgraph_main_path_can_use_multiple_mcp_tools_before_final_answer():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "kb_readiness_check",
                            "args": {"kbCode": "day20-cn-kb"},
                            "id": "call-1",
                        }
                    ],
                ),
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "retrieval_config_inspect",
                            "args": {},
                            "id": "call-2",
                        }
                    ],
                ),
                _final_answer_message("已完成 readiness 和检索配置检查，未执行任何写操作。"),
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查知识库 readiness 和检索配置后给出诊断",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert response.recommended_actions == []
    tool_steps = [step.tool_name for step in response.steps if step.step_type == "TOOL_CALL"]
    assert "kb.readiness.check" in tool_steps
    assert "retrieval.config.inspect" in tool_steps
    assert "未执行任何写操作" in response.summary


def test_langgraph_main_path_turns_write_intent_into_recommended_action():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "request_embedding_rebuild_submit",
                            "args": {"kbCode": "day20-cn-kb"},
                            "id": "action-1",
                        }
                    ],
                ),
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="诊断这个知识库为什么不能问答",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert response.summary == "Agent 已生成待确认动作：embedding.rebuild.submit"
    assert response.recommended_actions[0].tool_name == "embedding.rebuild.submit"
    assert response.recommended_actions[0].risk_level == "MEDIUM"
    assert response.recommended_actions[0].requires_confirmation is True
    assert any(step.node_name == "create_recommended_action" for step in response.steps)


def test_langgraph_main_path_fails_when_tool_call_fails():
    runtime = AgentRuntime(
        tool_client=FailingToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "system_health_check",
                            "args": {},
                            "id": "call-1",
                        }
                    ],
                )
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="诊断这个知识库为什么不能问答",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "backend unavailable" in response.error_message
    assert any(step.status == "FAILED" and step.step_type == "TOOL_CALL" for step in response.steps)


def test_execute_readonly_tool_passes_model_arguments_to_tool_client():
    tool_client = CapturingToolClient()
    runtime = AgentRuntime(
        tool_client=tool_client,
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "qa_retrieve_probe",
                            "args": {
                                "kbCode": "day20-cn-kb",
                                "question": "改写后的问题",
                                "attributes": {"topK": 3},
                            },
                            "id": "call-1",
                        }
                    ],
                ),
                _final_answer_message("完成"),
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查检索",
        question="原始问题",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert tool_client.executed == [
        (
            "qa.retrieve.probe",
            {"kbCode": "day20-cn-kb", "question": "改写后的问题", "attributes": {"topK": 3}},
        )
    ]
    tool_step = next(step for step in response.steps if step.node_name == "execute_readonly_tool")
    step_input = json.loads(tool_step.input_json)
    assert step_input["originalQuestion"] == "原始问题"
    assert step_input["toolQuestion"] == "改写后的问题"


def test_kb_code_mismatch_is_validation_failure():
    runtime = AgentRuntime(
        tool_client=TestToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "qa_retrieve_probe",
                            "args": {"kbCode": "other-kb", "question": "Q"},
                            "id": "call-1",
                        }
                    ],
                )
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查检索",
        question="原始问题",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "kbCode must match request.kbCode" in response.error_message
    assert not any(step.step_type == "TOOL_CALL" and step.status == "SUCCEEDED" for step in response.steps)


def test_schema_mismatch_is_validation_failure():
    runtime = AgentRuntime(
        tool_client=SchemaToolClient(),
        chat_model=ToolCallingFakeChatModel(responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "schema_test",
                            "args": {"count": "not-a-number"},
                            "id": "call-1",
                        }
                    ],
                )
            ]),
    )
    request = AgentRuntimeRequest(
        runCode="AR-test",
        kbCode="day20-cn-kb",
        goal="检查 schema",
    )

    response = runtime.run(request)

    assert response.status == "FAILED"
    assert "Argument arguments.count must be integer" in response.error_message


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


def test_mcp_agent_tool_client_definitions_use_langchain_adapter(monkeypatch):
    class FakeLangChainTool:
        name = "kb.readiness.check"
        description = "检查 readiness"
        args_schema = {"type": "object", "properties": {"kbCode": {"type": "string"}}}
        metadata = {
            "x-rag.executionMode": "READ_ONLY",
            "x-rag.maxRiskLevel": "LOW",
            "x-rag.requiresConfirmation": False,
        }

    class FakeMultiServerMCPClient:
        def __init__(self, connections, **kwargs):
            self.connections = connections
            self.kwargs = kwargs

        async def get_tools(self, *, server_name=None):
            assert server_name == "java-rag-tools"
            assert self.connections["java-rag-tools"]["transport"] == "streamable_http"
            assert self.connections["java-rag-tools"]["url"] == "http://java-backend/api/internal/mcp"
            assert self.kwargs["handle_tool_errors"] is False
            return [FakeLangChainTool()]

    monkeypatch.setattr("app.agent.tools.mcp_client.MultiServerMCPClient", FakeMultiServerMCPClient)
    client = McpAgentToolClient(Settings(mcp_tool_base_url="http://java-backend"))

    definitions = client.definitions()

    assert definitions[0].name == "kb.readiness.check"
    assert definitions[0].schema_version == "langchain-mcp-adapter"
    assert definitions[0].input_schema["properties"]["kbCode"]["type"] == "string"
    assert definitions[0].source_type == "MCP"


def test_mcp_agent_tool_client_fails_when_langchain_adapter_drops_rag_metadata(monkeypatch):
    class FakeLangChainTool:
        name = "unsafe.default"
        description = "metadata missing"
        args_schema = {}
        metadata = {}

    class FakeMultiServerMCPClient:
        def __init__(self, connections, **kwargs):
            pass

        async def get_tools(self, *, server_name=None):
            return [FakeLangChainTool()]

    monkeypatch.setattr("app.agent.tools.mcp_client.MultiServerMCPClient", FakeMultiServerMCPClient)
    client = McpAgentToolClient(Settings(mcp_tool_base_url="http://java-backend"))

    with pytest.raises(ValueError, match="missing x-rag annotations"):
        client.definitions()


def test_mcp_agent_tool_client_execute_uses_langchain_tool_and_parses_structured_content(monkeypatch):
    created_clients = []

    class FakeArtifact:
        def __init__(self, structured_content):
            self.structured_content = structured_content

    class FakeLangChainTool:
        name = "kb.readiness.check"
        description = "检查 readiness"
        args_schema = {"type": "object", "properties": {"question": {"type": "string"}}}
        metadata = {
            "x-rag.executionMode": "READ_ONLY",
            "x-rag.maxRiskLevel": "LOW",
            "x-rag.requiresConfirmation": False,
        }

        def __init__(self, owner):
            self.owner = owner
            self.tool_call = None

        async def ainvoke(self, tool_call):
            self.tool_call = tool_call
            interceptor = self.owner.kwargs["tool_interceptors"][0]
            request = FakeInterceptorRequest()

            async def handler(updated_request):
                self.owner.injected_headers = updated_request.headers
                return None

            await interceptor(request, handler)
            return ToolMessage(
                content='{"questionAnsweringReady":true}',
                artifact=FakeArtifact({"questionAnsweringReady": True}),
                tool_call_id=tool_call["id"],
                name=self.name,
            )

    class FakeInterceptorRequest:
        headers = None

    class FakeMultiServerMCPClient:
        def __init__(self, connections, **kwargs):
            self.connections = connections
            self.kwargs = kwargs
            self.injected_headers = None
            created_clients.append(self)

        async def get_tools(self, *, server_name=None):
            return [FakeLangChainTool(self)]

    monkeypatch.setattr("app.agent.tools.mcp_client.MultiServerMCPClient", FakeMultiServerMCPClient)
    client = McpAgentToolClient(Settings(mcp_tool_base_url="http://java-backend"))
    execution = client.execute(
        "kb.readiness.check",
        AgentRuntimeRequest(runCode="AR-test", kbCode="day20-cn-kb", goal="诊断", question="原始问题"),
        {"question": "改写问题"},
    )

    assert execution.success is True
    assert execution.output == {"questionAnsweringReady": True}
    assert created_clients[0].injected_headers == {
        "X-Rag-Run-Code": "AR-test",
        "X-Rag-Operator": "agent-runtime",
    }


def test_default_tool_client_uses_mcp_client(monkeypatch):
    monkeypatch.setattr("app.agent.runtime.get_settings", lambda: Settings(agent_tool_client="mcp"))

    assert isinstance(_default_tool_client(), McpAgentToolClient)


class ToolCallingFakeChatModel(FakeMessagesListChatModel):
    def bind_tools(self, tools, **kwargs):
        return self


def _final_answer_message(summary: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[
            {
                "name": AgentFinalAnswer.__name__,
                "args": {"summary": summary},
                "id": "final-1",
            }
        ],
    )


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
        arguments = dict(arguments or {})
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
            attributes = arguments.get("attributes") if isinstance(arguments.get("attributes"), dict) else {}
            output = {
                "question": arguments.get("question"),
                "topK": attributes.get("topK", 5),
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
                    "additionalProperties": False,
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
            AgentToolDefinition(
                toolName="qa.retrieve.probe",
                inputSchema={
                    "type": "object",
                    "required": ["kbCode", "question"],
                    "properties": {
                        "kbCode": {"type": "string"},
                        "question": {"type": "string"},
                        "attributes": {
                            "type": "object",
                            "properties": {"topK": {"type": "integer", "minimum": 1, "maximum": 10}},
                            "required": [],
                            "additionalProperties": False,
                        },
                    },
                    "additionalProperties": False,
                },
            ),
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
            output={
                "question": (arguments or {}).get("question"),
                "topK": ((arguments or {}).get("attributes") or {}).get("topK"),
            },
            duration_ms=1,
        )


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
                    "attributes": {
                        "type": "object",
                        "properties": {"topK": {"type": "integer", "minimum": 1, "maximum": 10}},
                        "required": [],
                        "additionalProperties": False,
                    },
                },
                "additionalProperties": False,
            },
        ),
        AgentToolDefinition(toolName="retrieval.config.inspect"),
    ]


def _runtime_events(frames) -> list[dict]:
    """从 Runtime SSE frame 中解析 data JSON，忽略 heartbeat comment。"""
    events: list[dict] = []
    for frame in frames:
        for line in frame.splitlines():
            if line.startswith("data: "):
                events.append(json.loads(line.removeprefix("data: ")))
    return events
