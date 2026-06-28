import type {
  AgentAction,
  AgentRun,
  AgentRunEvent,
  AgentRunEventConnectionStatus,
  AgentStep
} from "../../types/agent";
import { truncateText } from "../../utils/format";

export type AgentAnswerStatus = "empty" | "running" | "completed" | "waiting_confirmation" | "failed";
export type AgentProgressPhase =
  | "idle"
  | "started"
  | "planning"
  | "tooling"
  | "summarizing"
  | "waiting_confirmation"
  | "completed"
  | "failed";
export type AgentDecisionStatus = "generated" | "active" | "resolved" | "superseded";
export type AgentDecisionOutcomeStatus =
  | "pending"
  | "running"
  | "succeeded"
  | "failed"
  | "waiting_confirmation"
  | "confirmed"
  | "rejected"
  | "unknown";

export type AgentDecisionTraceItem = {
  id: string;
  stepId?: string;
  action: "CALL_TOOL" | "FINAL_ANSWER" | "REQUEST_CONFIRMATION" | string;
  title: string;
  reasonSummary?: string;
  toolName?: string;
  riskLevel?: string;
  decisionStatus: AgentDecisionStatus;
  outcomeStatus: AgentDecisionOutcomeStatus;
  outcomeSummary?: string;
  durationMs?: number;
  attemptCount?: number;
  sourceEventId: string;
  createdAt?: string;
};

export type AgentToolCallItem = {
  id: string;
  nodeInvocationId?: string;
  toolName: string;
  status: "running" | "completed" | "failed";
  summary?: string;
  durationMs?: number;
  sourceEventIds: string[];
  errorMessage?: string;
};

export type AgentEvidenceItem = {
  id: string;
  title: string;
  kind: "readiness" | "retrieval_probe" | "retrieval_config";
  summary: string;
  sourceStepCode?: string;
  sourceEventId?: string;
  data?: unknown;
};

export type AgentRunViewModel = {
  answer: {
    status: AgentAnswerStatus;
    text?: string;
    errorMessage?: string;
    sourceEventId?: string;
    runCode?: string;
  };
  progress: {
    currentPhase: AgentProgressPhase;
    seenPhases: AgentProgressPhase[];
    currentLabel: string;
    connectionStatus: AgentRunEventConnectionStatus;
    lastEventAt?: string;
  };
  planner: {
    label: "Planner 决策耗时" | "LLM 决策耗时";
    totalDurationMs: number;
    slowestDurationMs?: number;
    calls: AgentPlannerDurationItem[];
  };
  decisionTrace: AgentDecisionTraceItem[];
  recommendedActions: AgentAction[];
  tools: AgentToolCallItem[];
  evidence: AgentEvidenceItem[];
  debug: {
    runCode?: string;
    events: AgentRunEvent[];
    sanitizedEvents: AgentRunEvent[];
    rawPayloadByEventId: Record<string, unknown>;
    sanitizedPayloadByEventId: Record<string, unknown>;
    parseErrors: Array<{ eventId: string; message: string }>;
  };
};

export type AgentPlannerDurationItem = {
  id: string;
  nodeInvocationId?: string;
  sourceEventId?: string;
  stepCode?: string;
  durationMs: number;
  attemptCount?: number;
  status: "succeeded" | "failed" | "unknown";
  createdAt?: string;
};

type BuildOptions = {
  run?: AgentRun;
  events: AgentRunEvent[];
  connectionStatus: AgentRunEventConnectionStatus;
  connectionError?: string;
};

type ParsedEvent = {
  event: AgentRunEvent;
  payload?: JsonObject;
  runtimePayload?: JsonObject;
  decision?: JsonObject;
};

type JsonObject = Record<string, unknown>;

const sensitiveFieldNames = new Set([
  "apikey",
  "accesskey",
  "secretkey",
  "privatekey",
  "token",
  "secret",
  "authorization",
  "password",
  "credential"
]);

/** 将 SSE 审计事件投影成用户态 Agent 展示模型。 */
export function buildAgentRunViewModel({
  run,
  events,
  connectionStatus,
  connectionError
}: BuildOptions): AgentRunViewModel {
  const orderedEvents = orderEvents(events);
  const parseErrors: Array<{ eventId: string; message: string }> = [];
  const rawPayloadByEventId: Record<string, unknown> = {};
  const sanitizedPayloadByEventId: Record<string, unknown> = {};
  const parsedEvents = orderedEvents.map((event) => {
    const parsed = parseEventPayload(event, parseErrors);
    if (parsed.payload !== undefined) {
      rawPayloadByEventId[event.eventId] = parsed.payload;
      sanitizedPayloadByEventId[event.eventId] = sanitizeDebugValue(parsed.payload);
    }
    return parsed;
  });

  const planner = buildPlannerDurations(run, parsedEvents);
  return {
    answer: buildAnswer(run, parsedEvents),
    progress: buildProgress(run, parsedEvents, connectionStatus, connectionError),
    planner,
    decisionTrace: buildDecisionTrace(run, parsedEvents, planner.calls),
    recommendedActions: run?.actions ?? [],
    tools: buildToolCalls(parsedEvents),
    evidence: buildEvidence(run),
    debug: {
      runCode: run?.runCode,
      events: orderedEvents,
      sanitizedEvents: orderedEvents.map(sanitizeEvent),
      rawPayloadByEventId,
      sanitizedPayloadByEventId,
      parseErrors
    }
  };
}

/** Debug 展示层基础脱敏；这不是安全边界，后端仍需在源头控制敏感数据。 */
export function sanitizeDebugValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sanitizeDebugValue);
  }
  if (isObject(value)) {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [
        key,
        sensitiveFieldNames.has(key.toLowerCase()) ? "[REDACTED]" : sanitizeDebugValue(child)
      ])
    );
  }
  if (typeof value === "string" && value.length > 4000) {
    return `${value.slice(0, 4000)}...`;
  }
  return value;
}

function orderEvents(events: AgentRunEvent[]) {
  const byId = new Map<string, AgentRunEvent>();
  for (const event of events) {
    if (!byId.has(event.eventId)) {
      byId.set(event.eventId, event);
    }
  }
  return Array.from(byId.values()).sort((left, right) => {
    const leftId = Number.isFinite(left.databaseId) ? left.databaseId : 0;
    const rightId = Number.isFinite(right.databaseId) ? right.databaseId : 0;
    return leftId - rightId;
  });
}

function parseEventPayload(
  event: AgentRunEvent,
  parseErrors: Array<{ eventId: string; message: string }>
): ParsedEvent {
  const payload = parseJsonObject(event.payloadJson, event.eventId, parseErrors);
  const runtimePayload = asObject(payload?.runtimePayload);
  const decision = asObject(runtimePayload?.decision);
  return { event, payload, runtimePayload, decision };
}

function parseJsonObject(
  value: string | undefined,
  eventId: string,
  parseErrors: Array<{ eventId: string; message: string }>
) {
  if (!value) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (isObject(parsed)) {
      return parsed;
    }
    parseErrors.push({ eventId, message: "payloadJson is not an object" });
    return undefined;
  } catch (error) {
    parseErrors.push({
      eventId,
      message: error instanceof Error ? error.message : "Failed to parse payloadJson"
    });
    return undefined;
  }
}

function buildAnswer(run: AgentRun | undefined, parsedEvents: ParsedEvent[]): AgentRunViewModel["answer"] {
  const terminal = [...parsedEvents].reverse().find(({ event }) => event.terminal);
  const terminalSummary = textValue(terminal?.runtimePayload?.summary);
  if (terminalSummary) {
    return {
      status: answerStatusFromRun(run),
      text: terminalSummary,
      sourceEventId: terminal?.event.eventId,
      runCode: run?.runCode
    };
  }

  const finalReport = findFinalReportSummary(run?.steps);
  if (finalReport) {
    return {
      status: answerStatusFromRun(run),
      text: finalReport,
      runCode: run?.runCode
    };
  }

  if (run?.summary) {
    return {
      status: answerStatusFromRun(run),
      text: run.summary,
      runCode: run.runCode
    };
  }

  const finalAnswer = run && run.status !== "RUNNING" ? findFinalAnswer(parsedEvents, run.steps) : undefined;
  if (finalAnswer) {
    return {
      status: answerStatusFromRun(run),
      text: finalAnswer.text,
      sourceEventId: finalAnswer.sourceEventId,
      runCode: run?.runCode
    };
  }

  if (run?.status === "FAILED") {
    return {
      status: "failed",
      errorMessage: run.errorMessage || "Agent run 执行失败。",
      runCode: run.runCode
    };
  }
  if (run?.status === "WAITING_CONFIRMATION") {
    return {
      status: "waiting_confirmation",
      text: "Agent 已生成需要人工确认的推荐动作。",
      runCode: run.runCode
    };
  }
  if (run?.status === "SUCCEEDED") {
    return {
      status: "completed",
      text: "Agent 已完成，但本次运行没有返回可展示摘要。",
      runCode: run.runCode
    };
  }
  return {
    status: run ? "running" : "empty",
    text: run ? "正在生成诊断结论。" : undefined,
    runCode: run?.runCode
  };
}

function buildProgress(
  run: AgentRun | undefined,
  parsedEvents: ParsedEvent[],
  connectionStatus: AgentRunEventConnectionStatus,
  connectionError?: string
): AgentRunViewModel["progress"] {
  const last = parsedEvents.length > 0 ? parsedEvents[parsedEvents.length - 1].event : undefined;
  const seenPhases = collectSeenPhases(run, parsedEvents);
  if (!run) {
    return { currentPhase: "idle", seenPhases, currentLabel: "尚未创建 Agent run", connectionStatus };
  }
  if (run.status === "FAILED") {
    return {
      currentPhase: "failed",
      seenPhases,
      currentLabel: run.errorMessage || connectionError || "Agent run 执行失败",
      connectionStatus,
      lastEventAt: last?.createdAt
    };
  }
  if (run.status === "WAITING_CONFIRMATION") {
    return {
      currentPhase: "waiting_confirmation",
      seenPhases,
      currentLabel: "等待人工确认推荐动作",
      connectionStatus,
      lastEventAt: last?.createdAt
    };
  }
  if (run.status === "SUCCEEDED") {
    return {
      currentPhase: "completed",
      seenPhases,
      currentLabel: "诊断已完成",
      connectionStatus,
      lastEventAt: last?.createdAt
    };
  }

  const latestType = last?.type;
  if (latestType === "PLANNER_DECISION") {
    return { currentPhase: "planning", seenPhases, currentLabel: "分析问题并规划下一步", connectionStatus, lastEventAt: last?.createdAt };
  }
  if (latestType?.startsWith("TOOL_CALL") || latestType === "OBSERVATION_CREATED") {
    return { currentPhase: "tooling", seenPhases, currentLabel: "调用工具并汇总观察结果", connectionStatus, lastEventAt: last?.createdAt };
  }
  if (latestType === "STEP_COMPLETED") {
    return { currentPhase: "summarizing", seenPhases, currentLabel: "汇总证据并生成结论", connectionStatus, lastEventAt: last?.createdAt };
  }
  if (latestType === "RUN_STARTED" || latestType === "STEP_STARTED") {
    return { currentPhase: "started", seenPhases, currentLabel: "启动 Agent 执行流程", connectionStatus, lastEventAt: last?.createdAt };
  }
  return { currentPhase: "started", seenPhases, currentLabel: "Agent 正在运行", connectionStatus, lastEventAt: last?.createdAt };
}

function collectSeenPhases(run: AgentRun | undefined, parsedEvents: ParsedEvent[]) {
  const phases = new Set<AgentProgressPhase>();
  if (!run) {
    phases.add("idle");
    return Array.from(phases);
  }
  for (const { event } of parsedEvents) {
    if (event.type === "RUN_STARTED") phases.add("started");
    if (event.type === "PLANNER_DECISION") phases.add("planning");
    if (event.type.startsWith("TOOL_CALL") || event.type === "OBSERVATION_CREATED") phases.add("tooling");
    if (event.type === "STEP_COMPLETED") phases.add("summarizing");
    if (event.type === "RUN_WAITING_CONFIRMATION" || event.type === "ACTION_RECOMMENDED") phases.add("waiting_confirmation");
    if (event.type === "RUN_COMPLETED") phases.add("completed");
    if (event.type === "RUN_FAILED" || event.type === "STEP_FAILED" || event.type === "TOOL_CALL_FAILED") phases.add("failed");
  }
  if (run.status === "WAITING_CONFIRMATION") phases.add("waiting_confirmation");
  if (run.status === "SUCCEEDED") phases.add("completed");
  if (run.status === "FAILED") phases.add("failed");
  return Array.from(phases);
}

function buildPlannerDurations(run: AgentRun | undefined, parsedEvents: ParsedEvent[]) {
  const items = new Map<string, AgentPlannerDurationItem>();

  parsedEvents.forEach(({ event, runtimePayload }, index) => {
    if (event.type !== "PLANNER_DECISION" && !(event.type === "STEP_FAILED" && event.nodeName === "llm_plan")) {
      return;
    }
    const durationMs = numberValue(runtimePayload?.durationMs);
    if (durationMs === undefined) {
      return;
    }
    const key = plannerDurationKey({
      nodeInvocationId: event.nodeInvocationId,
      eventId: event.eventId,
      createdAt: event.createdAt,
      order: index
    });
    items.set(key, {
      id: key,
      nodeInvocationId: event.nodeInvocationId,
      sourceEventId: event.eventId,
      durationMs,
      attemptCount: numberValue(runtimePayload?.attemptCount),
      status: event.type === "STEP_FAILED" ? "failed" : event.status === "FAILED" ? "failed" : "succeeded",
      createdAt: event.createdAt
    });
  });

  // runtime event 是实时来源；只有完全缺失 runtime duration 时，才用终态 step duration 回填，避免重复统计。
  if (items.size === 0) {
    for (const [index, step] of (run?.steps ?? []).entries()) {
      if (step.stepType !== "LLM_DECISION" || step.durationMs === undefined) {
        continue;
      }
      const output = parseStepOutput(step);
      const key = plannerDurationKey({
        stepCode: step.stepCode,
        createdAt: step.createdAt,
        order: index
      });
      items.set(key, {
        id: key,
        stepCode: step.stepCode,
        durationMs: step.durationMs,
        attemptCount: numberValue(output?.attemptCount),
        status: step.status === "FAILED" ? "failed" : step.status === "SUCCEEDED" ? "succeeded" : "unknown",
        createdAt: step.createdAt
      });
    }
  }

  const calls = Array.from(items.values());
  return {
    label: "Planner 决策耗时" as const,
    calls,
    totalDurationMs: calls.reduce((total, call) => total + call.durationMs, 0),
    slowestDurationMs: calls.reduce<number | undefined>(
      (slowest, call) => (slowest === undefined || call.durationMs > slowest ? call.durationMs : slowest),
      undefined
    )
  };
}

function plannerDurationKey({
  nodeInvocationId,
  eventId,
  stepCode,
  createdAt,
  order
}: {
  nodeInvocationId?: string;
  eventId?: string;
  stepCode?: string;
  createdAt?: string;
  order: number;
}) {
  if (nodeInvocationId) return `node:${nodeInvocationId}`;
  if (eventId) return `event:${eventId}`;
  if (stepCode && createdAt) return `step:${stepCode}:${createdAt}`;
  if (stepCode) return `step:${stepCode}:order:${order}`;
  return `order:${order}`;
}

function findPlannerDurationForEvent(plannerDurations: AgentPlannerDurationItem[], event: AgentRunEvent) {
  if (event.nodeInvocationId) {
    const byNode = plannerDurations.find((item) => item.nodeInvocationId === event.nodeInvocationId);
    if (byNode) return byNode;
  }
  return plannerDurations.find((item) => item.sourceEventId === event.eventId);
}

function buildDecisionTrace(
  run: AgentRun | undefined,
  parsedEvents: ParsedEvent[],
  plannerDurations: AgentPlannerDurationItem[]
) {
  const decisions: AgentDecisionTraceItem[] = parsedEvents
    .filter(({ event }) => event.type === "PLANNER_DECISION")
    .map(({ event, decision }) => {
      const action = textValue(decision?.action) || "UNKNOWN";
      const toolName = textValue(decision?.toolName);
      const riskLevel = textValue(decision?.riskLevel);
      const plannerDuration = findPlannerDurationForEvent(plannerDurations, event);
      return {
        id: event.nodeInvocationId || event.eventId,
        stepId: event.nodeInvocationId,
        action,
        title: decisionTitle(action, toolName),
        reasonSummary: reasonSummary(textValue(decision?.reason)),
        toolName,
        riskLevel,
        decisionStatus: "generated" as AgentDecisionStatus,
        outcomeStatus: "pending" as AgentDecisionOutcomeStatus,
        durationMs: plannerDuration?.durationMs,
        attemptCount: plannerDuration?.attemptCount,
        sourceEventId: event.eventId,
        createdAt: event.createdAt
      };
    });

  const toolCalls = buildToolCalls(parsedEvents);
  for (const tool of toolCalls) {
    const relatedDecision = findDecisionForTool(decisions, tool);
    if (!relatedDecision) continue;
    relatedDecision.decisionStatus = tool.status === "running" ? "active" : "resolved";
    relatedDecision.outcomeStatus =
      tool.status === "running" ? "running" : tool.status === "failed" ? "failed" : "succeeded";
    relatedDecision.outcomeSummary =
      tool.status === "failed"
        ? `执行结果：工具调用失败${tool.errorMessage ? `，${tool.errorMessage}` : ""}`
        : tool.status === "completed"
          ? tool.summary
            ? `执行结果：${tool.summary}`
            : "执行结果：工具调用完成"
          : "执行结果：工具调用中";
  }

  for (const { event } of parsedEvents) {
    if (event.type === "ACTION_RECOMMENDED" || event.type === "RUN_WAITING_CONFIRMATION") {
      const decision = findLatestDecision(decisions, "REQUEST_CONFIRMATION");
      if (decision) {
        decision.decisionStatus = "resolved";
        decision.outcomeStatus = "waiting_confirmation";
        decision.outcomeSummary = "执行结果：等待人工确认";
      }
    }
    if (event.type === "RUN_COMPLETED") {
      const decision = findLatestDecision(decisions, "FINAL_ANSWER");
      if (decision) {
        decision.decisionStatus = "resolved";
        decision.outcomeStatus = "succeeded";
        decision.outcomeSummary = "执行结果：已生成最终回答";
      }
    }
  }

  for (const step of run?.steps ?? []) {
    if (step.nodeName === "final_report") {
      const decision = findLatestDecision(decisions, "FINAL_ANSWER");
      if (decision) {
        decision.decisionStatus = "resolved";
        decision.outcomeStatus = "succeeded";
        decision.outcomeSummary = "执行结果：已生成最终回答";
      }
    }
  }

  for (const action of run?.actions ?? []) {
    const decision = findLatestDecision(decisions, "REQUEST_CONFIRMATION", action.toolName);
    if (!decision) continue;
    if (action.status === "CONFIRMED" || action.status === "EXECUTING" || action.status === "SUCCEEDED") {
      decision.decisionStatus = "resolved";
      decision.outcomeStatus = "confirmed";
      decision.outcomeSummary = "执行结果：已确认执行";
    }
    if (action.status === "REJECTED") {
      decision.decisionStatus = "resolved";
      decision.outcomeStatus = "rejected";
      decision.outcomeSummary = "执行结果：已拒绝";
    }
    if (action.status === "FAILED") {
      decision.decisionStatus = "resolved";
      decision.outcomeStatus = "failed";
      decision.outcomeSummary = action.errorMessage ? `执行结果：${action.errorMessage}` : "执行结果：动作执行失败";
    }
  }

  return decisions;
}

function buildToolCalls(parsedEvents: ParsedEvent[]) {
  const calls: AgentToolCallItem[] = [];
  const byInvocation = new Map<string, AgentToolCallItem>();

  for (const { event, runtimePayload } of parsedEvents) {
    if (!event.type.startsWith("TOOL_CALL")) continue;
    const toolName = event.toolName || "unknown";
    const invocationId = event.nodeInvocationId;
    let item = invocationId ? byInvocation.get(invocationId) : undefined;

    // 当前 Python execute_readonly_tool 一次 nodeInvocationId 只对应一次工具调用。
    // 若未来一个 node 内多次工具调用，需要把配对键升级到 tool call id。
    if (!item && invocationId) {
      item = createToolItem(invocationId, toolName, invocationId);
      byInvocation.set(invocationId, item);
      calls.push(item);
    }
    if (!item) {
      item = [...calls].reverse().find(
        (candidate) => !candidate.nodeInvocationId && candidate.toolName === toolName && candidate.status === "running"
      );
    }
    if (!item) {
      item = createToolItem(`${event.eventId}-tool`, toolName, undefined);
      calls.push(item);
    }

    item.sourceEventIds.push(event.eventId);
    if (event.type === "TOOL_CALL_STARTED") {
      item.status = "running";
    }
    if (event.type === "TOOL_CALL_COMPLETED") {
      item.status = "completed";
      item.durationMs = numberValue(runtimePayload?.durationMs);
      item.summary = summarizeRuntimePayload(runtimePayload);
    }
    if (event.type === "TOOL_CALL_FAILED") {
      item.status = "failed";
      item.durationMs = numberValue(runtimePayload?.durationMs);
      item.errorMessage = textValue(runtimePayload?.errorMessage) || event.message;
      item.summary = item.errorMessage;
    }
  }
  return calls;
}

function createToolItem(id: string, toolName: string, nodeInvocationId: string | undefined): AgentToolCallItem {
  return { id, nodeInvocationId, toolName, status: "running", sourceEventIds: [] };
}

function buildEvidence(run: AgentRun | undefined) {
  const evidence: AgentEvidenceItem[] = [];
  for (const step of run?.steps ?? []) {
    const output = parseStepOutput(step);
    const raw = asObject(output?.raw);
    if (!raw) continue;
    if (step.toolName === "kb.readiness.check") {
      evidence.push({
        id: step.stepCode,
        title: "问答 readiness",
        kind: "readiness",
        sourceStepCode: step.stepCode,
        summary: [
          `问答就绪：${booleanText(raw.questionAnsweringReady)}`,
          `需重嵌：${booleanText(raw.reembedRequired)}`,
          `已嵌入 chunk：${textValue(raw.embeddedChunkCount) || "-"}`
        ].join("；"),
        data: {
          questionAnsweringReady: raw.questionAnsweringReady,
          reembedRequired: raw.reembedRequired,
          embeddedChunkCount: raw.embeddedChunkCount,
          nextStep: raw.nextStep
        }
      });
    }
    if (step.toolName === "qa.retrieve.probe") {
      const dense = asObject(raw.dense);
      const hybrid = asObject(raw.hybrid);
      evidence.push({
        id: step.stepCode,
        title: "Dense / Hybrid 检索探测",
        kind: "retrieval_probe",
        sourceStepCode: step.stepCode,
        summary: [
          `Dense 命中：${textValue(dense?.hitCount) || "-"}`,
          `Hybrid 命中：${textValue(hybrid?.hitCount) || "-"}`,
          `TopK：${textValue(raw.topK) || "-"}`
        ].join("；"),
        data: { topK: raw.topK, dense, hybrid, signals: raw.signals }
      });
    }
    if (step.toolName === "retrieval.config.inspect") {
      evidence.push({
        id: step.stepCode,
        title: "检索配置",
        kind: "retrieval_config",
        sourceStepCode: step.stepCode,
        summary: [
          `默认模式：${textValue(raw.defaultMode) || "-"}`,
          `默认 TopK：${textValue(raw.defaultTopK) || "-"}`,
          `Keyword 策略：${textValue(raw.keywordStrategy) || "-"}`
        ].join("；"),
        data: {
          defaultMode: raw.defaultMode,
          defaultTopK: raw.defaultTopK,
          keywordStrategy: raw.keywordStrategy
        }
      });
    }
  }
  return evidence;
}

function sanitizeEvent(event: AgentRunEvent): AgentRunEvent {
  if (!event.payloadJson) {
    return event;
  }
  try {
    return {
      ...event,
      payloadJson: JSON.stringify(sanitizeDebugValue(JSON.parse(event.payloadJson)), null, 2)
    };
  } catch {
    return {
      ...event,
      payloadJson: truncateText(event.payloadJson, 4000)
    };
  }
}

function findFinalReportSummary(steps: AgentStep[] | undefined) {
  const finalReport = [...(steps ?? [])].reverse().find((step) => step.nodeName === "final_report");
  const output = parseStepOutput(finalReport);
  return textValue(output?.summary);
}

function findFinalAnswer(parsedEvents: ParsedEvent[], steps: AgentStep[] | undefined) {
  const eventDecision = [...parsedEvents]
    .reverse()
    .find(({ decision }) => textValue(decision?.action) === "FINAL_ANSWER");
  const eventFinalAnswer = textValue(eventDecision?.decision?.finalAnswer);
  if (eventFinalAnswer) {
    return { text: eventFinalAnswer, sourceEventId: eventDecision?.event.eventId };
  }

  const step = [...(steps ?? [])].reverse().find((candidate) => candidate.stepType === "LLM_DECISION");
  const output = parseStepOutput(step);
  const decision = asObject(output?.decision);
  const stepFinalAnswer = textValue(decision?.finalAnswer);
  return stepFinalAnswer ? { text: stepFinalAnswer } : undefined;
}

function parseStepOutput(step: AgentStep | undefined) {
  if (!step?.outputJson) {
    return undefined;
  }
  try {
    return asObject(JSON.parse(step.outputJson) as unknown);
  } catch {
    return undefined;
  }
}

function answerStatusFromRun(run: AgentRun | undefined): AgentAnswerStatus {
  if (!run) return "empty";
  if (run.status === "FAILED") return "failed";
  if (run.status === "WAITING_CONFIRMATION") return "waiting_confirmation";
  if (run.status === "SUCCEEDED") return "completed";
  return "running";
}

function decisionTitle(action: string, toolName?: string) {
  if (action === "CALL_TOOL") {
    return toolName ? `Agent 决定调用「${toolName}」` : "Agent 决定调用工具";
  }
  if (action === "FINAL_ANSWER") {
    return "Agent 判断当前信息已足够，开始生成最终回答";
  }
  if (action === "REQUEST_CONFIRMATION") {
    return "Agent 建议执行一个需要确认的操作";
  }
  return "Agent 生成了一条决策";
}

function reasonSummary(reason?: string) {
  if (!reason) return undefined;
  return truncateText(reason.replace(/\s+/g, " ").trim(), 180);
}

function findDecisionForTool(decisions: AgentDecisionTraceItem[], tool: AgentToolCallItem) {
  if (tool.nodeInvocationId) {
    const byStep = decisions.find((decision) => decision.stepId === tool.nodeInvocationId);
    if (byStep) return byStep;
  }
  return [...decisions].reverse().find(
    (decision) => decision.action === "CALL_TOOL" && decision.toolName === tool.toolName
  );
}

function findLatestDecision(decisions: AgentDecisionTraceItem[], action: string, toolName?: string) {
  return [...decisions].reverse().find(
    (decision) => decision.action === action && (!toolName || decision.toolName === toolName)
  );
}

function summarizeRuntimePayload(payload?: JsonObject) {
  const summary = asObject(payload?.summary);
  if (summary) {
    const entries = Object.entries(summary)
      .filter(([, value]) => value !== undefined && value !== null && typeof value !== "object")
      .slice(0, 3)
      .map(([key, value]) => `${key}: ${String(value)}`);
    if (entries.length > 0) {
      return entries.join("；");
    }
  }
  return textValue(payload?.errorMessage);
}

function booleanText(value: unknown) {
  if (value === true) return "是";
  if (value === false) return "否";
  return "-";
}

function textValue(value: unknown) {
  if (value === undefined || value === null || value === "") return undefined;
  return String(value);
}

function numberValue(value: unknown) {
  if (typeof value === "number") return value;
  if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) {
    return Number(value);
  }
  return undefined;
}

function asObject(value: unknown): JsonObject | undefined {
  return isObject(value) ? value : undefined;
}

function isObject(value: unknown): value is JsonObject {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
