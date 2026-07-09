from __future__ import annotations

import json
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


AgentStepType = Literal["NODE", "TOOL_CALL", "REASONING", "LLM_DECISION"]
AgentStepStatus = Literal["PENDING", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"]
AgentActionRiskLevel = Literal["LOW", "MEDIUM", "HIGH"]
AgentToolExecutionMode = Literal["READ_ONLY", "REQUIRES_CONFIRMATION", "WRITE"]
AgentRuntimeEventType = Literal[
    "RUN_STARTED",
    "STEP_STARTED",
    "STEP_COMPLETED",
    "STEP_FAILED",
    "PLANNER_DECISION",
    "TOOL_CALL_STARTED",
    "TOOL_CALL_COMPLETED",
    "TOOL_CALL_FAILED",
    "OBSERVATION_CREATED",
    "ACTION_RECOMMENDED",
    "RUN_COMPLETED",
    "RUN_FAILED",
]


class AgentRuntimeRequest(BaseModel):
    """Java 调 Python Runtime 的请求协议。"""

    model_config = ConfigDict(populate_by_name=True)

    run_code: str = Field(alias="runCode")
    kb_code: str = Field(alias="kbCode")
    goal: str = Field(min_length=1)
    question: str | None = None


class AgentStepResult(BaseModel):
    """Python Runtime 返回给 Java 持久化的 step 草案。"""

    model_config = ConfigDict(populate_by_name=True)

    node_name: str = Field(alias="nodeName")
    tool_name: str | None = Field(default=None, alias="toolName")
    step_type: AgentStepType = Field(default="NODE", alias="stepType")
    status: AgentStepStatus = "PENDING"
    input_json: str | None = Field(default=None, alias="inputJson")
    output_json: str | None = Field(default=None, alias="outputJson")
    duration_ms: int | None = Field(default=None, alias="durationMs")
    error_message: str | None = Field(default=None, alias="errorMessage")


class AgentActionDraft(BaseModel):
    """Python 生成的待确认 action 草案，不包含 Java 生成的 actionCode。"""

    model_config = ConfigDict(populate_by_name=True)

    tool_name: str = Field(alias="toolName")
    title: str
    reason: str
    risk_level: AgentActionRiskLevel = Field(alias="riskLevel")
    requires_confirmation: bool = Field(default=True, alias="requiresConfirmation")
    action_payload: str | None = Field(default=None, alias="actionPayload")


class AgentToolDefinition(BaseModel):
    """工具注册表暴露给 planner 的工具契约。"""

    model_config = ConfigDict(populate_by_name=True)

    name: str = Field(alias="toolName")
    schema_version: str = Field(default="v2", alias="schemaVersion")
    description: str = ""
    input_schema: dict[str, Any] = Field(default_factory=dict, alias="inputSchema")
    output_schema: dict[str, Any] = Field(default_factory=dict, alias="outputSchema")
    execution_mode: AgentToolExecutionMode = Field(default="READ_ONLY", alias="executionMode")
    risk_level: AgentActionRiskLevel = Field(default="LOW", alias="maxRiskLevel")
    source_type: str = Field(default="JAVA", alias="sourceType")
    requires_confirmation: bool = Field(default=False, alias="requiresConfirmation")
    timeout_ms: int = Field(default=5000, alias="timeoutMs")

    @field_validator("input_schema", "output_schema", mode="before")
    @classmethod
    def _normalize_schema(cls, value: Any) -> dict[str, Any]:
        """兼容 Java 旧版字符串 schema 和新版结构化 JSON schema。"""
        if value is None:
            return {}
        if isinstance(value, dict):
            return value
        if isinstance(value, str):
            stripped = value.strip()
            if not stripped:
                return {}
            try:
                parsed = json.loads(stripped)
            except ValueError:
                return {"description": stripped}
            if isinstance(parsed, dict):
                return parsed
            return {"description": stripped}
        return {"description": str(value)}


class AgentObservation(BaseModel):
    """工具执行后的观察结果，供后续 planner 决策使用。"""

    model_config = ConfigDict(populate_by_name=True)

    tool_name: str = Field(alias="toolName")
    success: bool
    output: dict[str, Any] | None = None
    summary: dict[str, Any] = Field(default_factory=dict)
    error_message: str | None = Field(default=None, alias="errorMessage")
    duration_ms: int = Field(default=0, alias="durationMs")


class AgentRuntimeResponse(BaseModel):
    """Python Runtime 对 Java 的统一响应协议。"""

    model_config = ConfigDict(populate_by_name=True)

    status: Literal["SUCCEEDED", "FAILED"]
    summary: str | None = None
    steps: list[AgentStepResult] = Field(default_factory=list)
    recommended_actions: list[AgentActionDraft] = Field(
        default_factory=list,
        alias="recommendedActions",
    )
    error_message: str | None = Field(default=None, alias="errorMessage")


class AgentRuntimeEvent(BaseModel):
    """Python Runtime 通过 SSE 发给 Java 的内部事件。"""

    model_config = ConfigDict(populate_by_name=True)

    event_id: str = Field(alias="eventId")
    run_code: str = Field(alias="runCode")
    type: AgentRuntimeEventType
    node_invocation_id: str | None = Field(default=None, alias="nodeInvocationId")
    node_name: str | None = Field(default=None, alias="nodeName")
    tool_name: str | None = Field(default=None, alias="toolName")
    status: str | None = None
    message: str | None = None
    payload: dict[str, Any] = Field(default_factory=dict)
    terminal: bool = False
    created_at: str = Field(alias="createdAt")


class AgentState(BaseModel):
    """Pydantic 版状态模型，保留给文档化和后续强类型演进使用。"""

    request: AgentRuntimeRequest
    tools: list[AgentToolDefinition] = Field(default_factory=list)
    messages: list[dict[str, Any]] = Field(default_factory=list)
    observations: list[AgentObservation] = Field(default_factory=list)
    tool_call_count: int = Field(default=0, alias="toolCallCount")
    tool_results: dict[str, Any] = Field(default_factory=dict)
    steps: list[AgentStepResult] = Field(default_factory=list)
    recommended_actions: list[AgentActionDraft] = Field(default_factory=list)
    summary: str | None = None
    error_message: str | None = None
