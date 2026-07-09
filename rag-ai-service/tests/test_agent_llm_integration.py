import os

import pytest

from app.agent.runtime import AgentRuntime
from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools import AgentToolExecution
from app.core.config import Settings


def _has_chat_api_key(settings: Settings) -> bool:
    return bool(settings.chat_api_key or settings.deepseek_api_key or settings.openai_api_key or settings.dashscope_api_key)


@pytest.mark.skipif(os.getenv("RUN_REAL_LLM_TESTS") != "1", reason="real LLM smoke is opt-in")
def test_real_langgraph_agent_can_drive_readiness_probe_and_final_answer():
    settings = Settings(agent_planner_temperature=0)
    if not _has_chat_api_key(settings):
        pytest.skip("CHAT_API_KEY or compatible provider key is required for real LLM smoke")

    tool_client = SmokeToolClient()
    runtime = AgentRuntime(tool_client=tool_client)
    request = AgentRuntimeRequest(
        runCode="AR-real-llm-smoke",
        kbCode="finance-kb",
        goal=(
            "请严格按顺序完成：先调用 kb_readiness_check 工具，"
            "观察成功后调用 qa_retrieve_probe 工具，"
            "观察成功后用结构化最终答案总结。"
        ),
        question="结算异常怎么处理？",
    )

    response = runtime.run(request)

    assert response.status == "SUCCEEDED"
    assert response.recommended_actions == []
    assert tool_client.calls == ["kb.readiness.check", "qa.retrieve.probe"]
    assert response.summary


class SmokeToolClient:
    def __init__(self) -> None:
        self.calls: list[str] = []

    def definitions(self) -> list[AgentToolDefinition]:
        return [
            AgentToolDefinition(toolName="kb.readiness.check", description="检查知识库 readiness。"),
            AgentToolDefinition(
                toolName="qa.retrieve.probe",
                description="执行 Dense / Hybrid 检索探测。",
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
        self.calls.append(tool_name)
        if tool_name == "kb.readiness.check":
            return AgentToolExecution(
                tool_name=tool_name,
                success=True,
                output={
                    "kbCode": request.kb_code,
                    "questionAnsweringReady": True,
                    "reembedRequired": False,
                    "nextStep": "READY_TO_ASK",
                },
                duration_ms=1,
            )
        if tool_name == "qa.retrieve.probe":
            return AgentToolExecution(
                tool_name=tool_name,
                success=True,
                output={
                    "question": (arguments or {}).get("question") or request.question,
                    "topK": ((arguments or {}).get("attributes") or {}).get("topK", 5),
                    "dense": {"retrievalMode": "DENSE", "hitCount": 1},
                    "hybrid": {"retrievalMode": "HYBRID", "hitCount": 1, "keywordHitCount": 1},
                    "signals": {"denseEmpty": False, "hybridEmpty": False, "keywordZeroHit": False},
                },
                duration_ms=1,
            )
        return AgentToolExecution(tool_name=tool_name, success=False, error_message=f"unexpected tool: {tool_name}")
