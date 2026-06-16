from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


AgentRunMode = Literal["DIAGNOSE_ONLY", "DIAGNOSE_AND_RECOMMEND"]
AgentStepType = Literal["NODE", "TOOL_CALL", "REASONING"]
AgentStepStatus = Literal["PENDING", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"]
AgentActionRiskLevel = Literal["LOW", "MEDIUM", "HIGH"]


class AgentRuntimeRequest(BaseModel):
    run_code: str = Field(alias="runCode")
    kb_code: str = Field(alias="kbCode")
    goal: str = Field(min_length=1)
    question: str | None = None
    run_mode: AgentRunMode = Field(default="DIAGNOSE_AND_RECOMMEND", alias="runMode")


class AgentStepResult(BaseModel):
    node_name: str = Field(alias="nodeName")
    tool_name: str | None = Field(default=None, alias="toolName")
    step_type: AgentStepType = Field(default="NODE", alias="stepType")
    status: AgentStepStatus = "PENDING"
    input_json: str | None = Field(default=None, alias="inputJson")
    output_json: str | None = Field(default=None, alias="outputJson")
    duration_ms: int | None = Field(default=None, alias="durationMs")
    error_message: str | None = Field(default=None, alias="errorMessage")


class AgentActionDraft(BaseModel):
    tool_name: str = Field(alias="toolName")
    title: str
    reason: str
    risk_level: AgentActionRiskLevel = Field(alias="riskLevel")
    requires_confirmation: bool = Field(default=True, alias="requiresConfirmation")
    action_payload: str | None = Field(default=None, alias="actionPayload")


class AgentRuntimeResponse(BaseModel):
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
    tool_results: dict[str, Any] = Field(default_factory=dict)
    steps: list[AgentStepResult] = Field(default_factory=list)
    recommended_actions: list[AgentActionDraft] = Field(default_factory=list)
    summary: str | None = None
    error_message: str | None = None
