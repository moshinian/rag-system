from __future__ import annotations

import re
from typing import Any

from langchain_core.tools import StructuredTool

from app.agent.policies.risk import requires_confirmation
from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools.definitions import recommended_action_definitions
from app.agent.tools.protocol import AgentToolClient


class LangChainToolCatalog:
    """Build model-safe LangChain tool schemas for LangGraph model nodes."""

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
        """Return all tools visible to the LangGraph agent_model node."""
        result: list[StructuredTool] = []
        for definition in self._tool_client.definitions():
            if requires_confirmation(definition):
                continue
            result.append(self._readonly_tool(definition))
        for definition in recommended_action_definitions():
            result.append(self._recommended_action_tool(definition))
        return result

    def _readonly_tool(self, definition: AgentToolDefinition) -> StructuredTool:
        alias = self._alias(definition.name)
        self.alias_to_canonical[alias] = definition.name
        self._alias_to_definition[alias] = definition

        def invoke_tool(**kwargs: Any) -> dict[str, Any]:
            return {
                "toolName": definition.name,
                "arguments": kwargs,
                "execution": "handled_by_langgraph_execute_readonly_tool_node",
            }

        return StructuredTool.from_function(
            invoke_tool,
            name=alias,
            description=f"{definition.description} Canonical toolName: {definition.name}.",
            args_schema=definition.input_schema,
        )

    def _recommended_action_tool(self, definition: AgentToolDefinition) -> StructuredTool:
        alias = f"request_{self._alias(definition.name)}"
        self.alias_to_canonical[alias] = definition.name
        self._alias_to_definition[alias] = definition
        self._action_aliases.add(alias)

        def request_action(**kwargs: Any) -> dict[str, Any]:
            return {
                "toolName": definition.name,
                "arguments": kwargs,
                "execution": "handled_by_langgraph_create_recommended_action_node",
            }

        return StructuredTool.from_function(
            request_action,
            name=alias,
            description=(
                f"Create a Java human-confirmed action draft for {definition.name}. "
                "This does not execute the action."
            ),
            args_schema=definition.input_schema,
        )

    def canonical_name(self, alias: str) -> str:
        """Return Java/MCP canonical toolName for a model-safe alias."""
        return self.alias_to_canonical.get(alias, alias)

    def definition_for_alias(self, alias: str) -> AgentToolDefinition | None:
        """Return the tool definition behind a model-safe alias."""
        return self._alias_to_definition.get(alias)

    def is_action_alias(self, alias: str) -> bool:
        """Return whether an alias represents a local request_* action tool."""
        return alias in self._action_aliases

    def _alias(self, canonical_name: str) -> str:
        alias = re.sub(r"[^0-9A-Za-z_-]+", "_", canonical_name).strip("_")
        if not alias or not re.match(r"^[A-Za-z_]", alias):
            alias = f"tool_{alias}"
        original = alias
        counter = 2
        while alias in self.alias_to_canonical and self.alias_to_canonical[alias] != canonical_name:
            alias = f"{original}_{counter}"
            counter += 1
        return alias
