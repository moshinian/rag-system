from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.state import AgentRuntimeRequest


@dataclass(frozen=True)
class NormalizedToolArguments:
    """工具调用参数的统一视图，确保 kbCode 权威来源始终是 Runtime request。"""

    arguments: dict[str, Any]
    attributes: dict[str, Any]
    kb_code: str
    question: str | None
    original_question: str | None
    tool_question: str | None


def normalize_tool_arguments(
    request: AgentRuntimeRequest,
    arguments: dict[str, Any] | None = None,
) -> NormalizedToolArguments:
    """补齐并校验 planner 传入的工具参数。

    LLM 可以改写检索 question、补充只读工具参数，但不能切换知识库。
    """
    raw = dict(arguments or {})
    requested_kb_code = raw.pop("kb_code", None) if "kb_code" in raw else raw.get("kbCode")
    if requested_kb_code is not None and str(requested_kb_code).strip() != request.kb_code:
        raise ValueError("decision.arguments.kbCode must match request.kbCode")

    tool_question = _normalize_optional_string(raw.get("question"))
    if tool_question is None:
        tool_question = request.question

    normalized: dict[str, Any] = dict(raw)
    normalized["kbCode"] = request.kb_code
    if tool_question is not None:
        normalized["question"] = tool_question

    attributes = {
        key: value
        for key, value in normalized.items()
        if key not in {"kbCode", "kb_code", "question"}
    }
    _validate_lightweight_attributes(attributes)
    return NormalizedToolArguments(
        arguments=normalized,
        attributes=attributes,
        kb_code=request.kb_code,
        question=tool_question,
        original_question=request.question,
        tool_question=tool_question,
    )


def tool_call_input_payload(normalized: NormalizedToolArguments) -> dict[str, Any]:
    """生成 TOOL_CALL step inputJson，保留原始问题和实际工具问题。"""
    return {
        "arguments": normalized.arguments,
        "originalQuestion": normalized.original_question,
        "toolQuestion": normalized.tool_question,
    }


def _normalize_optional_string(value: Any) -> str | None:
    """把空字符串视为未提供，其余值转成字符串供 Java/Python 工具使用。"""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _validate_lightweight_attributes(attributes: dict[str, Any]) -> None:
    """执行第一版轻量参数校验，避免引入完整 JSON Schema 依赖。"""
    if "topK" in attributes:
        value = attributes["topK"]
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError("Argument topK must be integer")
        if value < 1 or value > 10:
            raise ValueError("Argument topK must be between 1 and 10")

