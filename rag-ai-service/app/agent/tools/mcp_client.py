from __future__ import annotations

import asyncio
from queue import Queue
from time import perf_counter
from typing import Any
from uuid import uuid4

import httpx
from langchain_core.messages import ToolMessage
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_mcp_adapters.interceptors import MCPToolCallRequest

from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.agent.tools.protocol import AgentToolExecution
from app.core.config import Settings


class McpAgentToolClient:
    """基于 LangChain MCP adapter 的 Streamable HTTP tools 客户端。"""

    def __init__(
        self,
        settings: Settings,
        *,
        httpx_client_factory: Any | None = None,
    ) -> None:
        """创建 MCP client；测试可注入 httpx async client factory。"""
        self._settings = settings
        self._httpx_client_factory = httpx_client_factory

    def definitions(self) -> list[AgentToolDefinition]:
        """通过 LangChain MCP adapter 读取工具定义。"""
        tools = _run_async(self._langchain_tools())
        return [self._to_agent_tool_definition(tool) for tool in tools]

    def execute(
        self,
        tool_name: str,
        request: AgentRuntimeRequest,
        arguments: dict[str, Any] | None = None,
    ) -> AgentToolExecution:
        """通过 LangChain MCP tool 执行工具，并映射为 AgentToolExecution。"""
        started_at = perf_counter()
        try:
            output = _run_async(
                self._execute_langchain_tool(
                    tool_name,
                    dict(arguments or {}),
                    run_code=request.run_code,
                )
            )
            structured_content = _extract_structured_content(output)
            return AgentToolExecution(
                tool_name=tool_name,
                success=_tool_output_success(output),
                output=structured_content,
                error_message=_tool_output_error_message(output),
                duration_ms=self._elapsed_ms(started_at),
            )
        except Exception as exc:
            return AgentToolExecution(
                tool_name=tool_name,
                success=False,
                error_message=f"Failed to call MCP tool through LangChain adapter: {exc}",
                duration_ms=self._elapsed_ms(started_at),
            )

    async def _execute_langchain_tool(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        *,
        run_code: str,
    ) -> Any:
        """查找并执行一个 LangChain MCP tool。"""
        tools = await self._langchain_tools(run_code=run_code)
        tool = next((item for item in tools if getattr(item, "name", None) == tool_name), None)
        if tool is None:
            raise ValueError(f"Unknown MCP tool: {tool_name}")
        return await tool.ainvoke(
            {
                "type": "tool_call",
                "id": f"tc-{uuid4()}",
                "name": tool_name,
                "args": arguments,
            }
        )

    async def _langchain_tools(self, *, run_code: str | None = None):
        """创建 LangChain MCP client，并读取 Java MCP tools。"""
        client = MultiServerMCPClient(
            {
                "java-rag-tools": {
                    "transport": "streamable_http",
                    "url": self._endpoint_url(),
                    "headers": {
                        "Origin": self._settings.mcp_tool_origin,
                        "X-Agent-Tool-Token": self._settings.mcp_tool_token,
                        "MCP-Protocol-Version": self._settings.mcp_protocol_version,
                    },
                    "timeout": self._settings.http_connect_timeout_ms / 1000,
                    "sse_read_timeout": self._settings.http_read_timeout_ms / 1000,
                    "httpx_client_factory": self._httpx_client_factory or _default_httpx_client_factory,
                }
            },
            tool_interceptors=[_runtime_headers_interceptor(run_code)] if run_code else None,
            handle_tool_errors=False,
        )
        return await client.get_tools(server_name="java-rag-tools")

    def _to_agent_tool_definition(self, tool: Any) -> AgentToolDefinition:
        """把 LangChain MCP tool 映射为 planner 现有 ToolDefinition。"""
        metadata = getattr(tool, "metadata", None)
        if not isinstance(metadata, dict):
            raise ValueError(f"LangChain MCP tool missing metadata: {getattr(tool, 'name', '<unknown>')}")
        if "x-rag.executionMode" not in metadata or "x-rag.maxRiskLevel" not in metadata:
            raise ValueError(f"LangChain MCP tool missing x-rag annotations: {getattr(tool, 'name', '<unknown>')}")

        input_schema = getattr(tool, "args_schema", None)
        if not isinstance(input_schema, dict):
            input_schema = getattr(tool, "args", None)
        return AgentToolDefinition(
            toolName=str(getattr(tool, "name", "") or ""),
            schemaVersion="langchain-mcp-adapter",
            description=str(getattr(tool, "description", "") or getattr(tool, "name", "") or ""),
            inputSchema=input_schema if isinstance(input_schema, dict) else {},
            outputSchema={},
            executionMode=str(metadata["x-rag.executionMode"]),
            maxRiskLevel=str(metadata["x-rag.maxRiskLevel"]),
            sourceType="MCP",
            requiresConfirmation=bool(metadata.get("x-rag.requiresConfirmation") or False),
            timeoutMs=5000,
        )

    def _endpoint_url(self) -> str:
        """规范拼接 Java MCP base URL 与 endpoint。"""
        base_url = self._settings.mcp_tool_base_url.rstrip("/")
        endpoint = self._settings.mcp_tool_endpoint.strip()
        if not endpoint.startswith("/"):
            endpoint = f"/{endpoint}"
        return f"{base_url}{endpoint}"

    @staticmethod
    def _elapsed_ms(started_at: float) -> int:
        """计算工具调用耗时毫秒数。"""
        return max(0, int((perf_counter() - started_at) * 1000))


def _runtime_headers_interceptor(run_code: str | None):
    """把 Agent runtime 元数据通过 headers 传给 Java MCP endpoint。"""

    async def inject_runtime_headers(request: MCPToolCallRequest, handler):
        if run_code:
            request.headers = {
                **(request.headers or {}),
                "X-Rag-Run-Code": run_code,
                "X-Rag-Operator": "agent-runtime",
            }
        return await handler(request)

    return inject_runtime_headers


def _default_httpx_client_factory(
    headers: dict[str, str] | None = None,
    timeout: httpx.Timeout | None = None,
    auth: httpx.Auth | None = None,
) -> httpx.AsyncClient:
    """创建 adapter 使用的 AsyncClient，默认绕过环境代理。"""
    return httpx.AsyncClient(headers=headers, timeout=timeout, auth=auth, trust_env=False)


def _extract_structured_content(output: Any) -> dict[str, Any] | None:
    """从 LangChain MCP tool 输出中提取 MCP structuredContent。"""
    artifact = getattr(output, "artifact", None)
    structured = getattr(artifact, "structured_content", None)
    if isinstance(structured, dict):
        return structured
    if isinstance(output, dict):
        return output
    return None


def _tool_output_success(output: Any) -> bool:
    """判断 LangChain tool 输出是否成功。"""
    status = getattr(output, "status", None)
    return status != "error"


def _tool_output_error_message(output: Any) -> str | None:
    """从 LangChain ToolMessage 中提取错误文本。"""
    if _tool_output_success(output):
        return None
    if isinstance(output, ToolMessage):
        content = output.content
        if isinstance(content, str) and content.strip():
            return content.strip()
    return "MCP tool returned error"


def _run_async(coro):
    """在同步 Runtime 中安全执行 LangChain MCP adapter 的 async API。"""
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(coro)

    result_queue: Queue[tuple[bool, Any]] = Queue(maxsize=1)

    def runner() -> None:
        try:
            result_queue.put((True, asyncio.run(coro)))
        except Exception as exc:  # pragma: no cover - defensive bridge
            result_queue.put((False, exc))

    import threading

    thread = threading.Thread(target=runner, name="langchain-mcp-adapter", daemon=True)
    thread.start()
    thread.join()
    ok, result = result_queue.get()
    if ok:
        return result
    raise result
