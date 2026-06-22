from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


AgentRunMode = Literal["DIAGNOSE_ONLY", "DIAGNOSE_AND_RECOMMEND", "INTELLIGENT_TOOL_AGENT"]
AgentStepType = Literal["NODE", "TOOL_CALL", "REASONING", "LLM_DECISION"]
AgentStepStatus = Literal["PENDING", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"]
AgentActionRiskLevel = Literal["LOW", "MEDIUM", "HIGH"]
AgentDecisionAction = Literal["CALL_TOOL", "REQUEST_CONFIRMATION", "FINAL_ANSWER"]
AgentToolExecutionMode = Literal["READ_ONLY", "REQUIRES_CONFIRMATION", "WRITE"]


class AgentRuntimeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    run_code: str = Field(alias="runCode")
    kb_code: str = Field(alias="kbCode")
    goal: str = Field(min_length=1)
    question: str | None = None
    run_mode: AgentRunMode = Field(default="DIAGNOSE_AND_RECOMMEND", alias="runMode")


class AgentStepResult(BaseModel):
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
    model_config = ConfigDict(populate_by_name=True)

    tool_name: str = Field(alias="toolName")
    title: str
    reason: str
    risk_level: AgentActionRiskLevel = Field(alias="riskLevel")
    requires_confirmation: bool = Field(default=True, alias="requiresConfirmation")
    action_payload: str | None = Field(default=None, alias="actionPayload")


class AgentToolDefinition(BaseModel):
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


class AgentDecision(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    action: AgentDecisionAction
    tool_name: str | None = Field(default=None, alias="toolName")
    arguments: dict[str, Any] = Field(default_factory=dict)
    reason: str
    final_answer: str | None = Field(default=None, alias="finalAnswer")
    risk_level: AgentActionRiskLevel | None = Field(default=None, alias="riskLevel")


class AgentObservation(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    tool_name: str = Field(alias="toolName")
    success: bool
    output: dict[str, Any] | None = None
    summary: dict[str, Any] = Field(default_factory=dict)
    error_message: str | None = Field(default=None, alias="errorMessage")
    duration_ms: int = Field(default=0, alias="durationMs")


class AgentRuntimeResponse(BaseModel):
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
