from __future__ import annotations

from typing import Any

from app.agent.state import AgentDecision, AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools.arguments import normalize_tool_arguments
from app.agent.tools.definitions import recommended_action_definitions


def validate_decision(
    decision: AgentDecision,
    tools: list[AgentToolDefinition],
    request: AgentRuntimeRequest | None = None,
) -> None:
    """校验 planner 决策是否合法，失败时抛 ValueError 进入重试/失败路径。"""
    if decision.action == "FINAL_ANSWER":
        # 最终回答必须携带 finalAnswer，否则前端没有可展示结论。
        if not decision.final_answer:
            raise ValueError("FINAL_ANSWER requires finalAnswer")
        return

    if not decision.tool_name:
        # CALL_TOOL / REQUEST_CONFIRMATION 都必须明确目标工具。
        raise ValueError(f"{decision.action} requires toolName")

    catalog = tools if decision.action == "CALL_TOOL" else recommended_action_definitions()
    tool = tool_by_name(catalog, decision.tool_name)
    if tool is None:
        # CALL_TOOL 必须来自 MCP tools/list；REQUEST_CONFIRMATION 必须来自 action catalog。
        raise ValueError(f"Unknown toolName: {decision.tool_name}")

    if decision.action == "CALL_TOOL" and (
        tool.execution_mode != "READ_ONLY" or tool.requires_confirmation or tool.risk_level in {"MEDIUM", "HIGH"}
    ):
        raise ValueError(f"CALL_TOOL is not allowed for non-readonly or confirmation tool: {decision.tool_name}")

    arguments = decision.arguments
    if request is not None and decision.action == "CALL_TOOL":
        arguments = normalize_tool_arguments(request, arguments).arguments
    validate_arguments(arguments, tool.input_schema)


def validate_arguments(arguments: dict[str, Any], schema: dict[str, Any]) -> None:
    """执行最小 JSON schema 校验，覆盖当前工具参数所需的类型和必填字段。"""
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
            # 未声明字段暂不拦截，保留与现有 ToolDefinition 的宽松兼容。
            continue
        expected_type = spec.get("type")
        if expected_type == "string" and not isinstance(value, str):
            raise ValueError(f"Argument {name} must be string")
        if expected_type == "integer" and (isinstance(value, bool) or not isinstance(value, int)):
            raise ValueError(f"Argument {name} must be integer")
        if expected_type == "number" and (isinstance(value, bool) or not isinstance(value, (int, float))):
            raise ValueError(f"Argument {name} must be number")
        if expected_type == "boolean" and not isinstance(value, bool):
            raise ValueError(f"Argument {name} must be boolean")
        minimum = spec.get("minimum")
        maximum = spec.get("maximum")
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            if isinstance(minimum, (int, float)) and value < minimum:
                raise ValueError(f"Argument {name} must be >= {minimum}")
            if isinstance(maximum, (int, float)) and value > maximum:
                raise ValueError(f"Argument {name} must be <= {maximum}")


def tool_by_name(tools: list[AgentToolDefinition], tool_name: str) -> AgentToolDefinition | None:
    """按 toolName 从当前可见工具列表中查找定义。"""
    return next((tool for tool in tools if tool.name == tool_name), None)
