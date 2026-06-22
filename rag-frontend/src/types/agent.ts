export type AgentRunMode = "DIAGNOSE_ONLY" | "DIAGNOSE_AND_RECOMMEND" | "INTELLIGENT_TOOL_AGENT";

export type AgentRunStatus = "RUNNING" | "WAITING_CONFIRMATION" | "SUCCEEDED" | "FAILED";

export type AgentStepType = "NODE" | "TOOL_CALL" | "LLM_CALL" | "LLM_DECISION";

export type AgentStepStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export type AgentActionRiskLevel = "LOW" | "MEDIUM" | "HIGH";

export type AgentActionStatus =
  | "PENDING_CONFIRMATION"
  | "CONFIRMED"
  | "EXECUTING"
  | "SUCCEEDED"
  | "FAILED"
  | "REJECTED";

export type AgentRunCreatePayload = {
  goal: string;
  question?: string;
  runMode?: AgentRunMode;
  createdBy?: string;
};

export type AgentActionConfirmPayload = {
  operator?: string;
};

export type AgentActionRejectPayload = {
  operator?: string;
  reason?: string;
};

export type AgentStep = {
  stepCode: string;
  nodeName: string;
  toolName?: string;
  stepType: AgentStepType;
  status: AgentStepStatus;
  inputJson?: string;
  outputJson?: string;
  durationMs?: number;
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type AgentAction = {
  actionCode: string;
  toolName: string;
  title?: string;
  reason?: string;
  riskLevel: AgentActionRiskLevel;
  requiresConfirmation: boolean;
  status: AgentActionStatus;
  actionPayload?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  executedAt?: string;
  resultJson?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type AgentRun = {
  runCode: string;
  knowledgeBaseCode: string;
  goal: string;
  question?: string;
  runMode: AgentRunMode;
  status: AgentRunStatus;
  summary?: string;
  errorMessage?: string;
  steps: AgentStep[];
  actions: AgentAction[];
  createdBy: string;
  createdAt?: string;
  updatedAt?: string;
  finishedAt?: string;
};
