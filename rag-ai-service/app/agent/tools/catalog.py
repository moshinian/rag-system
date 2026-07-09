from __future__ import annotations

import re
from typing import Any

from langchain_core.tools import StructuredTool

from app.agent.policies.risk import requires_confirmation
from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools.definitions import recommended_action_definitions
from app.agent.tools.protocol import AgentToolClient


class LangChainToolCatalog:
    """为 LangGraph 模型节点构造模型安全的 LangChain 工具 schema。"""

    def __init__(
        self,
        *,
        tool_client: AgentToolClient,
        request: AgentRuntimeRequest,
    ) -> None:
        self._tool_client = tool_client
        self._request = request
        self.alias_to_canonical: dict[str, str] = {}
        self._alias_to_definition: dict[str, AgentToolDefinition] = {}
        self._action_aliases: set[str] = set()

    def tools(self) -> list[StructuredTool]:
        """返回 agent_model 节点可见的全部工具。"""
        result: list[StructuredTool] = []
        for definition in self._tool_client.definitions():
            # 需要确认的真实工具不直接暴露给模型执行，只通过 request_* 动作工具表达意图。
            if requires_confirmation(definition):
                continue
            result.append(self._readonly_tool(definition))
        for definition in recommended_action_definitions():
            result.append(self._recommended_action_tool(definition))
        return result

    def _readonly_tool(self, definition: AgentToolDefinition) -> StructuredTool:
        """把一个 Java/MCP 只读工具包装成模型可调用的 LangChain 工具。"""
        alias = self._alias(definition.name)
        self.alias_to_canonical[alias] = definition.name
        self._alias_to_definition[alias] = definition

        def invoke_tool(**kwargs: Any) -> dict[str, Any]:
            # 真正执行发生在 execute_readonly_tool 节点，这里只让模型形成标准 tool call。
            return {
                "toolName": definition.name,
                "arguments": kwargs,
                "execution": "handled_by_langgraph_execute_readonly_tool_node",
            }

        return StructuredTool.from_function(
            invoke_tool,
            name=alias,
            description=f"{definition.description} 标准工具名: {definition.name}。",
            args_schema=definition.input_schema,
        )

    def _recommended_action_tool(self, definition: AgentToolDefinition) -> StructuredTool:
        """把写操作工具包装成 request_* 待确认动作工具。"""
        alias = f"request_{self._alias(definition.name)}"
        self.alias_to_canonical[alias] = definition.name
        self._alias_to_definition[alias] = definition
        self._action_aliases.add(alias)

        def request_action(**kwargs: Any) -> dict[str, Any]:
            # 该工具只生成动作草案，不会直接触发重试、重建或其他写操作。
            return {
                "toolName": definition.name,
                "arguments": kwargs,
                "execution": "handled_by_langgraph_create_recommended_action_node",
            }

        return StructuredTool.from_function(
            request_action,
            name=alias,
            description=(
                f"为 {definition.name} 创建一个需要 Java 人工确认的动作草案。"
                "该工具不会直接执行动作。"
            ),
            args_schema=definition.input_schema,
        )

    def canonical_name(self, alias: str) -> str:
        """返回模型安全别名对应的 Java/MCP 标准工具名。"""
        return self.alias_to_canonical.get(alias, alias)

    def definition_for_alias(self, alias: str) -> AgentToolDefinition | None:
        """返回模型安全别名背后的工具定义。"""
        return self._alias_to_definition.get(alias)

    def is_action_alias(self, alias: str) -> bool:
        """判断别名是否代表本地 request_* 动作工具。"""
        return alias in self._action_aliases

    def _alias(self, canonical_name: str) -> str:
        """把 Java/MCP 工具名转换为模型可接受的函数名。"""
        alias = re.sub(r"[^0-9A-Za-z_-]+", "_", canonical_name).strip("_")
        if not alias or not re.match(r"^[A-Za-z_]", alias):
            alias = f"tool_{alias}"
        original = alias
        counter = 2
        while alias in self.alias_to_canonical and self.alias_to_canonical[alias] != canonical_name:
            alias = f"{original}_{counter}"
            counter += 1
        return alias
