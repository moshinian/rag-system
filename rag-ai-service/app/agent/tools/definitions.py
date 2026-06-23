from __future__ import annotations

from typing import Any

from app.agent.state import AgentToolDefinition
from app.core.config import Settings


def default_tool_definitions(settings: Settings | None = None) -> list[AgentToolDefinition]:
    """返回 MCP server 不可用时用于单测的只读工具契约。"""
    return default_java_tool_definitions()


def default_java_tool_definitions() -> list[AgentToolDefinition]:
    """定义 Java 侧工具的默认契约，用作 Java Tool Registry 不可用时的 fallback。"""
    return [
        tool_definition("system.health.check", "检查系统健康状态。", input_schema=object_schema({}, [])),
        tool_definition(
            "kb.readiness.check",
            "检查知识库问答 readiness。",
            input_schema=object_schema({"kbCode": {"type": "string"}}, ["kbCode"]),
        ),
        tool_definition(
            "documents.status.scan",
            "扫描知识库文档状态。",
            input_schema=object_schema({"kbCode": {"type": "string"}}, ["kbCode"]),
        ),
        tool_definition(
            "indexing.tasks.scan",
            "扫描索引任务状态。",
            input_schema=object_schema({"kbCode": {"type": "string"}}, ["kbCode"]),
        ),
        tool_definition(
            "qa.retrieve.probe",
            "执行 Dense / Hybrid 检索探测。",
            input_schema=object_schema(
                {
                    "kbCode": {"type": "string"},
                    "question": {"type": "string"},
                    "attributes": object_schema(
                        {"topK": {"type": "integer", "minimum": 1, "maximum": 10}},
                        [],
                    ),
                },
                ["kbCode", "question"],
            ),
        ),
        tool_definition(
            "retrieval.config.inspect",
            "检查当前检索配置和 Dense / Hybrid / keyword 行为参数。",
            input_schema=object_schema({}, []),
        ),
    ]


def recommended_action_definitions() -> list[AgentToolDefinition]:
    """REQUEST_CONFIRMATION 使用的推荐动作白名单；不属于 MCP tools/list。"""
    return [
        tool_definition(
            "document.indexing_task.retry",
            "重试失败索引任务，必须人工确认。",
            execution_mode="REQUIRES_CONFIRMATION",
            risk_level="MEDIUM",
            source_type="ACTION_CATALOG",
            requires_confirmation=True,
        ),
        tool_definition(
            "embedding.rebuild.submit",
            "提交重嵌入任务，必须人工确认。",
            execution_mode="REQUIRES_CONFIRMATION",
            risk_level="MEDIUM",
            source_type="ACTION_CATALOG",
            requires_confirmation=True,
        ),
    ]


def tool_definition(
    name: str,
    description: str,
    *,
    execution_mode: str = "READ_ONLY",
    risk_level: str = "LOW",
    source_type: str = "JAVA",
    requires_confirmation: bool = False,
    timeout_ms: int = 5000,
    input_schema: dict[str, Any] | None = None,
    output_schema: dict[str, Any] | None = None,
) -> AgentToolDefinition:
    """构造 ToolDefinition v2，保持 Java/Python 两侧字段别名一致。"""
    return AgentToolDefinition(
        toolName=name,
        schemaVersion="v2",
        description=description,
        inputSchema=input_schema or object_schema({}, []),
        outputSchema=output_schema or {"type": "object"},
        executionMode=execution_mode,
        maxRiskLevel=risk_level,
        sourceType=source_type,
        requiresConfirmation=requires_confirmation,
        timeoutMs=timeout_ms,
    )


def merge_tool_definitions(
    primary: list[AgentToolDefinition],
    fallback: list[AgentToolDefinition],
) -> list[AgentToolDefinition]:
    """合并工具定义，primary 同名工具优先生效。"""
    merged: dict[str, AgentToolDefinition] = {tool.name: tool for tool in primary}
    for tool in fallback:
        merged.setdefault(tool.name, tool)
    return list(merged.values())


def object_schema(properties: dict[str, Any], required: list[str]) -> dict[str, Any]:
    """构造 Java 内置 MCP tool 的 strict object schema。"""
    return {
        "type": "object",
        "properties": properties,
        "required": required,
        "additionalProperties": False,
    }
