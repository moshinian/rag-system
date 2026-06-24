from __future__ import annotations

import json
from typing import Any

from app.agent.graphs.state import AgentGraphState
from app.agent.state import AgentToolDefinition
from app.clients.openai_compatible_client import OpenAiCompatibleProviderClient, ProviderTarget
from app.core.config import Settings, get_settings


class LlmAgentDecisionClient:
    """基于真实 Chat provider 的 planner，只负责产出 AgentDecision JSON。"""

    def __init__(
        self,
        settings: Settings | None = None,
        *,
        provider_client: OpenAiCompatibleProviderClient | None = None,
    ) -> None:
        """创建 LLM planner，HTTP 调用复用项目现有 OpenAI-compatible client。"""
        self._settings = settings or get_settings()
        self._provider_client = provider_client or OpenAiCompatibleProviderClient(
            self._settings,
            read_timeout_ms=self._settings.agent_planner_timeout_ms,
        )

    def decide(self, state: AgentGraphState) -> str:
        """遵守 AgentDecisionClient 协议，返回严格 JSON 字符串。"""
        request = state["request"]
        # Planner 只复用 chat provider，不直接接触 Java、MCP 或写操作。
        target = ProviderTarget(
            capability="chat",
            provider=self._settings.chat_provider,
            base_url=self._settings.chat_base_url,
            api_key=self._settings.chat_api_key,
            default_model=self._settings.chat_default_model,
            path=self._settings.chat_path,
        )
        payload = {
            "model": self._settings.agent_planner_model or self._settings.chat_default_model,
            "messages": [
                {"role": "system", "content": _system_prompt()},
                {"role": "user", "content": _planner_context(state)},
            ],
            "temperature": self._settings.agent_planner_temperature,
        }
        response = self._provider_client.post_json(target, payload, request.run_code)
        return _extract_message_content(response)


def _system_prompt() -> str:
    """返回不含业务秘密的 planner 系统约束。"""
    return (
        "You are the planner for a single LangGraph tool agent. "
        "Return strict JSON only, with fields matching AgentDecision: "
        "action, toolName, arguments, reason, finalAnswer, riskLevel. "
        "Allowed action values are CALL_TOOL, REQUEST_CONFIRMATION, FINAL_ANSWER. "
        "You must only call visible tools. You do not execute tools, shell, HTTP, Java, writes, or MCP. "
        "If a tool requires confirmation or is not read-only, use REQUEST_CONFIRMATION instead of CALL_TOOL. "
        "Do not include chain-of-thought, markdown, code fences, prompts, or raw provider metadata."
    )


def _planner_context(state: AgentGraphState) -> str:
    """组装 planner 输入上下文，保持观察摘要可控且可审计。"""
    request = state["request"]
    context = {
        "goal": request.goal,
        "question": request.question,
        "kbCode": request.kb_code,
        "visibleTools": [_tool_for_prompt(tool) for tool in state.get("tools", [])],
        "recentObservations": [_observation_for_prompt(item) for item in state.get("observations", [])[-4:]],
        "toolCallCount": int(state.get("tool_call_count") or 0),
        "maxToolCallCount": 6,
    }
    planner_error = state.get("planner_error_message")
    if planner_error:
        # 重试只提供裁剪后的校验错误，不回灌内部异常栈或完整原始输出。
        context["previousInvalidDecisionError"] = planner_error[:1000]
        context["retryInstruction"] = (
            "Your previous output was rejected by validation. "
            "Return only one corrected AgentDecision JSON object that satisfies the error. "
            "Do not add markdown, explanations, chain-of-thought, or raw tool output."
        )
    return json.dumps(context, ensure_ascii=False, separators=(",", ":"))


def _tool_for_prompt(tool: AgentToolDefinition) -> dict[str, Any]:
    """裁剪 ToolDefinition，只暴露 planner 决策需要的字段。"""
    return {
        "toolName": tool.name,
        "description": tool.description,
        "inputSchema": tool.input_schema,
        "outputSchema": tool.output_schema,
        "executionMode": tool.execution_mode,
        "maxRiskLevel": tool.risk_level,
        "requiresConfirmation": tool.requires_confirmation,
        "sourceType": tool.source_type,
    }


def _observation_for_prompt(observation: Any) -> dict[str, Any]:
    """只回灌 observation 摘要，避免工具完整输出膨胀 prompt。"""
    return {
        "toolName": observation.tool_name,
        "success": observation.success,
        "summary": observation.summary,
        "errorMessage": observation.error_message,
        "durationMs": observation.duration_ms,
    }


def _extract_message_content(response: dict[str, Any]) -> str:
    """从 OpenAI-compatible 响应中取 assistant message content。"""
    choices = response.get("choices")
    if not isinstance(choices, list) or not choices:
        raise ValueError("LLM planner response missing choices")
    first = choices[0]
    if not isinstance(first, dict):
        raise ValueError("LLM planner response choice is invalid")
    message = first.get("message")
    if not isinstance(message, dict):
        raise ValueError("LLM planner response missing message")
    content = message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise ValueError("LLM planner response content is empty")
    return content.strip()
