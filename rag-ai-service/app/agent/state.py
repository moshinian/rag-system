from __future__ import annotations

import json
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


AgentRunMode = Literal["DIAGNOSE_ONLY", "DIAGNOSE_AND_RECOMMEND", "INTELLIGENT_TOOL_AGENT"]
AgentStepType = Literal["NODE", "TOOL_CALL", "REASONING", "LLM_DECISION"]
AgentStepStatus = Literal["PENDING", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"]
AgentActionRiskLevel = Literal["LOW", "MEDIUM", "HIGH"]
AgentDecisionAction = Literal["CALL_TOOL", "REQUEST_CONFIRMATION", "FINAL_ANSWER"]
AgentToolExecutionMode = Literal["READ_ONLY", "REQUIRES_CONFIRMATION", "WRITE"]


class AgentRuntimeRequest(BaseModel):
    """Java 调 Python Runtime 的请求协议。"""

    model_config = ConfigDict(populate_by_name=True)

    run_code: str = Field(alias="runCode")
    kb_code: str = Field(alias="kbCode")
    goal: str = Field(min_length=1)
    question: str | None = None
    run_mode: AgentRunMode = Field(default="DIAGNOSE_AND_RECOMMEND", alias="runMode")


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
    """Tool Registry 暴露给 planner 的工具契约。"""

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


class AgentDecision(BaseModel):
    """planner 每一轮必须输出的严格 JSON 决策。"""

    model_config = ConfigDict(populate_by_name=True)

    action: AgentDecisionAction
    tool_name: str | None = Field(default=None, alias="toolName")
    arguments: dict[str, Any] = Field(default_factory=dict)
    reason: str
    final_answer: str | None = Field(default=None, alias="finalAnswer")
    risk_level: AgentActionRiskLevel | None = Field(default=None, alias="riskLevel")

    @field_validator("arguments", mode="before")
    @classmethod
    def _normalize_arguments(cls, value: Any) -> dict[str, Any]:
        """兼容 LLM 在 FINAL_ANSWER 场景把 arguments 返回为 null。"""
        if value is None:
            return {}
        return value


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


class AgentState(BaseModel):
    """Pydantic 版状态模型，保留给文档化和后续强类型演进使用。"""

    request: AgentRuntimeRequest
    tools: list[AgentToolDefinition] = Field(default_factory=list)
    messages: list[dict[str, Any]] = Field(default_factory=list)
    decision: AgentDecision | None = None
    observations: list[AgentObservation] = Field(default_factory=list)
    tool_call_count: int = Field(default=0, alias="toolCallCount")
    tool_results: dict[str, Any] = Field(default_factory=dict)
    steps: list[AgentStepResult] = Field(default_factory=list)
    recommended_actions: list[AgentActionDraft] = Field(default_factory=list)
    summary: str | None = None
    error_message: str | None = None
