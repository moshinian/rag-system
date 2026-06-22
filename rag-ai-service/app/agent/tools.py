from __future__ import annotations

import json
from dataclasses import dataclass
from time import perf_counter
from typing import Any, Protocol

import httpx

from app.agent.state import AgentRuntimeRequest, AgentToolDefinition
from app.core.config import Settings


@dataclass(frozen=True)
class AgentToolExecution:
    tool_name: str
    success: bool
    output: dict[str, Any] | None = None
    error_message: str | None = None
    duration_ms: int = 0


class AgentToolClient(Protocol):
    def definitions(self) -> list[AgentToolDefinition]:
        """Return the tools visible to the intelligent Agent runtime."""

    def execute(self, tool_name: str, request: AgentRuntimeRequest) -> AgentToolExecution:
        """Execute a Java-owned Agent tool and return a normalized observation."""


class StaticAgentToolClient:
    """Day 4/Day 6 replaceable tool client used before Java exposes runtime tool HTTP APIs."""

    def definitions(self) -> list[AgentToolDefinition]:
        return _default_tool_definitions()

    def execute(self, tool_name: str, request: AgentRuntimeRequest) -> AgentToolExecution:
        started_at = perf_counter()
        try:
            if tool_name == "system.health.check":
                output = self._system_health()
            elif tool_name == "kb.readiness.check":
                output = self._kb_readiness(request)
            elif tool_name == "documents.status.scan":
                output = self._documents_status(request)
            elif tool_name == "indexing.tasks.scan":
                output = self._indexing_tasks(request)
            elif tool_name == "qa.retrieve.probe":
                output = self._qa_retrieve_probe(request)
            elif tool_name == "mcp.repo.status.inspect":
                output = self._mcp_repo_status()
            elif tool_name == "cli.git.status":
                output = self._cli_git_status()
            else:
                return AgentToolExecution(
                    tool_name=tool_name,
                    success=False,
                    error_message=f"Unsupported Agent tool: {tool_name}",
                    duration_ms=self._elapsed_ms(started_at),
                )
            return AgentToolExecution(
                tool_name=tool_name,
                success=True,
                output=output,
                duration_ms=self._elapsed_ms(started_at),
            )
        except Exception as exc:  # pragma: no cover - defensive guard for future clients
            return AgentToolExecution(
                tool_name=tool_name,
                success=False,
                error_message=str(exc),
                duration_ms=self._elapsed_ms(started_at),
            )

    def _system_health(self) -> dict[str, Any]:
        return {
            "status": "UP",
            "serviceName": "rag-backend",
            "components": [
                {"name": "postgresql", "status": "UP"},
                {"name": "redis", "status": "UP"},
                {"name": "embedding", "status": "UP"},
                {"name": "llm", "status": "UP"},
            ],
        }

    def _kb_readiness(self, request: AgentRuntimeRequest) -> dict[str, Any]:
        goal_text = f"{request.goal} {request.question or ''}".lower()
        reembed_required = any(
            marker in goal_text
            for marker in ["不能问答", "reembed", "重嵌入", "readiness", "不可问答"]
        )
        return {
            "kbCode": request.kb_code,
            "questionAnsweringReady": not reembed_required,
            "knowledgeBaseStatus": "ACTIVE",
            "indexedChunkCount": 12,
            "embeddedChunkCount": 0 if reembed_required else 12,
            "reembedRequired": reembed_required,
            "reembedInProgress": False,
            "nextStep": "SUBMIT_REEMBEDDING" if reembed_required else "READY_TO_ASK",
        }

    def _documents_status(self, request: AgentRuntimeRequest) -> dict[str, Any]:
        has_failed_task = self._asks_for_failed_indexing_task(request)
        return {
            "kbCode": request.kb_code,
            "totalDocumentCount": 2,
            "statusCounts": {
                "UPLOADED": 0,
                "PARSING": 0,
                "PARSED": 0,
                "CHUNKING": 0,
                "INDEXED": 1 if has_failed_task else 2,
                "FAILED": 1 if has_failed_task else 0,
                "DISABLED": 0,
            },
            "failedDocuments": [
                {
                    "documentCode": "DOC-failed-demo",
                    "documentName": "索引失败样例.md",
                    "status": "FAILED",
                    "errorMessage": "Embedding provider failed",
                }
            ]
            if has_failed_task
            else [],
        }

    def _indexing_tasks(self, request: AgentRuntimeRequest) -> dict[str, Any]:
        has_failed_task = self._asks_for_failed_indexing_task(request)
        return {
            "kbCode": request.kb_code,
            "scannedTaskCount": 2,
            "statusCounts": {
                "QUEUED": 0,
                "RUNNING": 0,
                "SUCCEEDED": 1 if has_failed_task else 2,
                "FAILED": 1 if has_failed_task else 0,
            },
            "failedTasks": [
                {
                    "taskId": 1001,
                    "documentId": 2001,
                    "documentCode": "DOC-failed-demo",
                    "taskType": "DOCUMENT_INDEXING",
                    "taskStage": "DOCUMENT_EMBEDDING",
                    "retryCount": 1,
                    "maxRetryCount": 3,
                    "errorMessage": "Embedding provider failed",
                }
            ]
            if has_failed_task
            else [],
        }

    def _qa_retrieve_probe(self, request: AgentRuntimeRequest) -> dict[str, Any]:
        question = (request.question or "").strip()
        if not question:
            raise ValueError("question must not be blank for qa.retrieve.probe")

        dense_empty = self._asks_for_empty_retrieval(request)
        keyword_zero_hit = self._asks_for_keyword_zero_hit(request)
        dense_sources = [] if dense_empty else [self._source("DOC-dense-demo", "Dense 命中文档.md", 1, 0.82)]
        hybrid_sources = [] if dense_empty else [self._source("DOC-hybrid-demo", "Hybrid 命中文档.md", 2, 0.91)]
        keyword_hit_count = 0 if keyword_zero_hit else 1
        hybrid_no_gain = keyword_zero_hit and not dense_empty

        return {
            "question": question,
            "topK": 5,
            "dense": {
                "retrievalMode": "DENSE",
                "hitCount": len(dense_sources),
                "denseHitCount": len(dense_sources),
                "keywordHitCount": 0,
                "fusionStrategy": "NONE",
                "denseDurationMs": 10,
                "keywordDurationMs": 0,
                "fusionDurationMs": 0,
                "totalDurationMs": 10,
                "sources": dense_sources,
            },
            "hybrid": {
                "retrievalMode": "HYBRID",
                "hitCount": len(hybrid_sources),
                "denseHitCount": len(hybrid_sources),
                "keywordHitCount": keyword_hit_count,
                "fusionStrategy": "RRF",
                "denseDurationMs": 10,
                "keywordDurationMs": 5,
                "fusionDurationMs": 3,
                "totalDurationMs": 18,
                "sources": hybrid_sources if not hybrid_no_gain else dense_sources,
            },
            "signals": {
                "denseEmpty": dense_empty,
                "hybridEmpty": dense_empty,
                "keywordZeroHit": keyword_zero_hit,
                "hybridNoGain": hybrid_no_gain,
                "topSourceChanged": not dense_empty and not hybrid_no_gain,
            },
        }

    def _source(self, document_code: str, document_name: str, chunk_index: int, score: float) -> dict[str, Any]:
        return {
            "documentCode": document_code,
            "documentName": document_name,
            "chunkId": chunk_index,
            "chunkIndex": chunk_index,
            "score": score,
        }

    def _asks_for_failed_indexing_task(self, request: AgentRuntimeRequest) -> bool:
        goal_text = f"{request.goal} {request.question or ''}".lower()
        return any(
            marker in goal_text
            for marker in ["索引异常", "索引失败", "failed indexing", "failed task", "indexing task"]
        )

    def _asks_for_empty_retrieval(self, request: AgentRuntimeRequest) -> bool:
        goal_text = f"{request.goal} {request.question or ''}".lower()
        return any(marker in goal_text for marker in ["检索为空", "无命中", "no hit", "empty retrieval"])

    def _asks_for_keyword_zero_hit(self, request: AgentRuntimeRequest) -> bool:
        goal_text = f"{request.goal} {request.question or ''}".lower()
        return any(marker in goal_text for marker in ["keyword 零命中", "关键词零命中", "keyword zero"])

    def _mcp_repo_status(self) -> dict[str, Any]:
        return {
            "repository": "rag-system",
            "summary": "fake MCP repo inspection completed",
            "signals": ["agent-runtime-present", "tool-registry-present"],
        }

    def _cli_git_status(self) -> dict[str, Any]:
        return {
            "command": "git status --short",
            "mode": "READ_ONLY_TEMPLATE",
            "summary": "working tree status inspected by a whitelisted CLI adapter",
        }

    @staticmethod
    def _elapsed_ms(started_at: float) -> int:
        return max(0, int((perf_counter() - started_at) * 1000))


class JavaAgentToolClient:
    """HTTP client that calls Java-owned Agent tools."""

    def __init__(
        self,
        settings: Settings,
        *,
        http_client: httpx.Client | None = None,
    ) -> None:
        self._settings = settings
        self._client = http_client or httpx.Client(
            timeout=httpx.Timeout(
                connect=settings.http_connect_timeout_ms / 1000,
                read=settings.http_read_timeout_ms / 1000,
                write=settings.http_read_timeout_ms / 1000,
                pool=settings.http_connect_timeout_ms / 1000,
            ),
            trust_env=False,
        )

    def definitions(self) -> list[AgentToolDefinition]:
        try:
            response = self._client.get(
                self._definitions_url(),
                headers={"X-Agent-Tool-Token": self._settings.java_agent_tool_token},
            )
            if response.status_code < 200 or response.status_code >= 300:
                return _default_tool_definitions()
            envelope = response.json()
            data = envelope.get("data") if isinstance(envelope, dict) else None
            if not isinstance(data, list):
                return _default_tool_definitions()
            definitions = [AgentToolDefinition.model_validate(item) for item in data if isinstance(item, dict)]
            return _merge_tool_definitions(definitions, _default_non_java_tool_definitions())
        except Exception:
            return _default_tool_definitions()

    def execute(self, tool_name: str, request: AgentRuntimeRequest) -> AgentToolExecution:
        started_at = perf_counter()
        try:
            response = self._client.post(
                self._tool_url(tool_name),
                json={
                    "runCode": request.run_code,
                    "kbCode": request.kb_code,
                    "question": request.question,
                    "operator": "agent-runtime",
                    "attributes": {},
                },
                headers={
                    "X-Agent-Tool-Token": self._settings.java_agent_tool_token,
                    "X-Request-Id": request.run_code,
                },
            )
            if response.status_code < 200 or response.status_code >= 300:
                return AgentToolExecution(
                    tool_name=tool_name,
                    success=False,
                    error_message=self._http_error_message(response),
                    duration_ms=self._elapsed_ms(started_at),
                )

            envelope = response.json()
            data = envelope.get("data") if isinstance(envelope, dict) else None
            if not isinstance(data, dict):
                return AgentToolExecution(
                    tool_name=tool_name,
                    success=False,
                    error_message="Java Agent tool response data is empty",
                    duration_ms=self._elapsed_ms(started_at),
                )

            output_json = data.get("outputJson")
            output = json.loads(output_json) if isinstance(output_json, str) and output_json else None
            return AgentToolExecution(
                tool_name=str(data.get("toolName") or tool_name),
                success=bool(data.get("success")),
                output=output if isinstance(output, dict) else None,
                error_message=data.get("errorMessage"),
                duration_ms=int(data.get("durationMs") or self._elapsed_ms(started_at)),
            )
        except Exception as exc:
            return AgentToolExecution(
                tool_name=tool_name,
                success=False,
                error_message=f"Failed to call Java Agent tool: {exc}",
                duration_ms=self._elapsed_ms(started_at),
            )

    def _tool_url(self, tool_name: str) -> str:
        base_url = self._settings.java_agent_tool_base_url.rstrip("/")
        path = self._settings.java_agent_tool_execute_path_template.format(tool_name=tool_name).strip()
        if not path.startswith("/"):
            path = f"/{path}"
        return f"{base_url}{path}"

    def _definitions_url(self) -> str:
        base_url = self._settings.java_agent_tool_base_url.rstrip("/")
        execute_template = self._settings.java_agent_tool_execute_path_template
        path = execute_template.split("/{tool_name}", maxsplit=1)[0].strip()
        if not path.startswith("/"):
            path = f"/{path}"
        return f"{base_url}{path}"

    def _http_error_message(self, response: httpx.Response) -> str:
        try:
            body = response.json()
        except ValueError:
            return f"Java Agent tool HTTP {response.status_code}: {response.text}"
        if isinstance(body, dict):
            message = body.get("message")
            code = body.get("code")
            if message and code:
                return f"Java Agent tool HTTP {response.status_code} {code}: {message}"
            if message:
                return f"Java Agent tool HTTP {response.status_code}: {message}"
        return f"Java Agent tool HTTP {response.status_code}: {response.text}"

    @staticmethod
    def _elapsed_ms(started_at: float) -> int:
        return max(0, int((perf_counter() - started_at) * 1000))


def _default_tool_definitions() -> list[AgentToolDefinition]:
    return _merge_tool_definitions(_default_java_tool_definitions(), _default_non_java_tool_definitions())


def _default_java_tool_definitions() -> list[AgentToolDefinition]:
    return [
        _tool_definition("system.health.check", "检查系统健康状态。"),
        _tool_definition("kb.readiness.check", "检查知识库问答 readiness。"),
        _tool_definition("documents.status.scan", "扫描知识库文档状态。"),
        _tool_definition("indexing.tasks.scan", "扫描索引任务状态。"),
        _tool_definition("qa.retrieve.probe", "执行 Dense / Hybrid 检索探测。"),
        _tool_definition(
            "document.indexing_task.retry",
            "重试失败索引任务，必须人工确认。",
            execution_mode="REQUIRES_CONFIRMATION",
            risk_level="MEDIUM",
            requires_confirmation=True,
        ),
        _tool_definition(
            "embedding.rebuild.submit",
            "提交重嵌入任务，必须人工确认。",
            execution_mode="REQUIRES_CONFIRMATION",
            risk_level="MEDIUM",
            requires_confirmation=True,
        ),
    ]


def _default_non_java_tool_definitions() -> list[AgentToolDefinition]:
    return [
        _tool_definition(
            "mcp.repo.status.inspect",
            "Fake MCP repo inspection tool used to validate MCP tool discovery and observation flow.",
            source_type="MCP",
        ),
        _tool_definition(
            "cli.git.status",
            "Whitelisted read-only CLI adapter for git status inspection.",
            source_type="CLI",
        ),
    ]


def _tool_definition(
    name: str,
    description: str,
    *,
    execution_mode: str = "READ_ONLY",
    risk_level: str = "LOW",
    source_type: str = "JAVA",
    requires_confirmation: bool = False,
) -> AgentToolDefinition:
    return AgentToolDefinition(
        toolName=name,
        schemaVersion="v2",
        description=description,
        inputSchema={
            "type": "object",
            "properties": {
                "kbCode": {"type": "string"},
                "question": {"type": "string"},
            },
        },
        outputSchema={"type": "object"},
        executionMode=execution_mode,
        maxRiskLevel=risk_level,
        sourceType=source_type,
        requiresConfirmation=requires_confirmation,
        timeoutMs=5000,
    )


def _merge_tool_definitions(
    primary: list[AgentToolDefinition],
    fallback: list[AgentToolDefinition],
) -> list[AgentToolDefinition]:
    merged: dict[str, AgentToolDefinition] = {tool.name: tool for tool in primary}
    for tool in fallback:
        merged.setdefault(tool.name, tool)
    return list(merged.values())
