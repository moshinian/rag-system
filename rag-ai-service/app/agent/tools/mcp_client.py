from __future__ import annotations

from time import perf_counter
from typing import Any
from uuid import uuid4

import httpx

from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools.arguments import normalize_tool_arguments
from app.agent.tools.protocol import AgentToolExecution
from app.core.config import Settings


class McpAgentToolClient:
    """MCP Streamable HTTP tools capability 客户端。"""

    def __init__(
        self,
        settings: Settings,
        *,
        http_client: httpx.Client | None = None,
    ) -> None:
        """创建 MCP client；测试可注入 MockTransport。"""
        self._settings = settings
        self._client = http_client or httpx.Client(
            timeout=httpx.Timeout(
                connect=settings.http_connect_timeout_ms / 1000,
                read=settings.http_read_timeout_ms / 1000,
                write=settings.http_read_timeout_ms / 1000,
                pool=settings.http_connect_timeout_ms / 1000,
            ),
            # MCP endpoint 默认跑在本机 Java 后端，必须绕开环境代理。
            trust_env=False,
        )
        self._session_id: str | None = None

    def definitions(self) -> list[AgentToolDefinition]:
        """initialize -> initialized notification -> tools/list，并映射到 AgentToolDefinition。"""
        self._ensure_initialized()
        response = self._rpc("tools/list", {}, retry_on_missing_session=True)
        tools = response.get("tools")
        if not isinstance(tools, list):
            raise ValueError("MCP tools/list result.tools must be a list")
        return [self._to_agent_tool_definition(tool) for tool in tools if isinstance(tool, dict)]

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict[str, Any] | None = None,
    ) -> AgentToolExecution:
        """调用 MCP tools/call，并映射为 AgentToolExecution。"""
        started_at = perf_counter()
        try:
            self._ensure_initialized()
            normalized = normalize_tool_arguments(request, arguments)
            result = self._rpc(
                "tools/call",
                {
                    "name": tool_name,
                    "arguments": {
                        "runCode": request.run_code,
                        "kbCode": normalized.kb_code,
                        "question": normalized.question,
                        "operator": "agent-runtime",
                        "attributes": normalized.attributes,
                    },
                },
                retry_on_missing_session=True,
            )
            structured = result.get("structuredContent")
            return AgentToolExecution(
                tool_name=tool_name,
                success=not bool(result.get("isError")),
                output=structured if isinstance(structured, dict) else None,
                error_message=self._tool_error_message(result),
                duration_ms=self._elapsed_ms(started_at),
            )
        except Exception as exc:
            return AgentToolExecution(
                tool_name=tool_name,
                success=False,
                error_message=f"Failed to call MCP tool: {exc}",
                duration_ms=self._elapsed_ms(started_at),
            )

    def _ensure_initialized(self) -> None:
        """没有有效 session 时重新执行 MCP lifecycle。"""
        if self._session_id:
            return
        initialize = self._post(
            {
                "jsonrpc": "2.0",
                "id": self._request_id(),
                "method": "initialize",
                "params": {
                    "protocolVersion": self._settings.mcp_protocol_version,
                    "capabilities": {},
                    "clientInfo": {"name": "rag-ai-service", "version": self._settings.service_version},
                },
            },
            initialized=False,
        )
        self._session_id = initialize.headers.get("Mcp-Session-Id")
        if not self._session_id:
            raise ValueError("MCP initialize response missing Mcp-Session-Id")
        data = initialize.json()
        result = data.get("result") if isinstance(data, dict) else None
        if not isinstance(result, dict):
            raise ValueError("MCP initialize response missing result")
        if result.get("protocolVersion") != self._settings.mcp_protocol_version:
            raise ValueError("MCP protocol version mismatch")
        notification = self._post(
            {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}},
            initialized=True,
        )
        if notification.status_code != 202:
            raise ValueError(f"MCP initialized notification failed: HTTP {notification.status_code}")

    def _rpc(self, method: str, params: dict[str, Any], *, retry_on_missing_session: bool) -> dict[str, Any]:
        """发送 JSON-RPC request；session 404 时按协议重新 initialize 后重试一次。"""
        payload = {"jsonrpc": "2.0", "id": self._request_id(), "method": method, "params": params}
        response = self._post(payload, initialized=True)
        if response.status_code == 404 and retry_on_missing_session:
            self._session_id = None
            self._ensure_initialized()
            response = self._post(payload, initialized=True)
        if response.status_code < 200 or response.status_code >= 300:
            raise ValueError(f"MCP HTTP {response.status_code}: {response.text}")
        data = response.json()
        if isinstance(data, dict) and isinstance(data.get("error"), dict):
            error = data["error"]
            raise ValueError(f"MCP JSON-RPC error {error.get('code')}: {error.get('message')}")
        result = data.get("result") if isinstance(data, dict) else None
        if not isinstance(result, dict):
            raise ValueError("MCP JSON-RPC response missing result")
        return result

    def _post(self, payload: dict[str, Any], *, initialized: bool) -> httpx.Response:
        """向 Java MCP endpoint 发送单个 JSON-RPC 对象。"""
        headers = {
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "Origin": self._settings.mcp_tool_origin,
            "X-Agent-Tool-Token": self._settings.mcp_tool_token,
        }
        if initialized:
            headers["MCP-Protocol-Version"] = self._settings.mcp_protocol_version
            if self._session_id:
                headers["Mcp-Session-Id"] = self._session_id
        return self._client.post(self._endpoint_url(), json=payload, headers=headers)

    def _to_agent_tool_definition(self, tool: dict[str, Any]) -> AgentToolDefinition:
        """把 MCP tool definition 映射为 planner 现有 ToolDefinition。"""
        annotations = tool.get("annotations") if isinstance(tool.get("annotations"), dict) else {}
        return AgentToolDefinition(
            toolName=tool.get("name"),
            schemaVersion="mcp-2025-06-18",
            description=str(tool.get("description") or tool.get("title") or tool.get("name") or ""),
            inputSchema=tool.get("inputSchema") if isinstance(tool.get("inputSchema"), dict) else {},
            outputSchema={},
            executionMode=str(annotations.get("x-rag.executionMode") or "READ_ONLY"),
            maxRiskLevel=str(annotations.get("x-rag.maxRiskLevel") or "LOW"),
            sourceType="MCP",
            requiresConfirmation=bool(annotations.get("x-rag.requiresConfirmation") or False),
            timeoutMs=5000,
        )

    def _endpoint_url(self) -> str:
        base_url = self._settings.mcp_tool_base_url.rstrip("/")
        endpoint = self._settings.mcp_tool_endpoint.strip()
        if not endpoint.startswith("/"):
            endpoint = f"/{endpoint}"
        return f"{base_url}{endpoint}"

    def _tool_error_message(self, result: dict[str, Any]) -> str | None:
        if not bool(result.get("isError")):
            return None
        content = result.get("content")
        if isinstance(content, list) and content and isinstance(content[0], dict):
            text = content[0].get("text")
            return str(text) if text else "MCP tool returned isError=true"
        return "MCP tool returned isError=true"

    def _request_id(self) -> str:
        return f"mcp-{uuid4()}"

    @staticmethod
    def _elapsed_ms(started_at: float) -> int:
        return max(0, int((perf_counter() - started_at) * 1000))
