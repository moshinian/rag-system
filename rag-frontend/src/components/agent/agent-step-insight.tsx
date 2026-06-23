import { BranchesOutlined, CheckCircleOutlined, ExperimentOutlined, SettingOutlined } from "@ant-design/icons";
import { Alert, Descriptions, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { AgentStep } from "../../types/agent";

type JsonObject = Record<string, unknown>;

type AgentStepInsightProps = {
  step: AgentStep;
};

type MetricRow = {
  key: string;
  label: string;
  dense?: unknown;
  hybrid?: unknown;
};

/** 将 Agent step 的 JSON 输出解析成面向走读的摘要。 */
export function AgentStepInsight({ step }: AgentStepInsightProps) {
  const input = parseJsonObject(step.inputJson);
  const output = parseJsonObject(step.outputJson);

  if (step.stepType === "LLM_DECISION") {
    return <DecisionInsight output={output} />;
  }
  if (step.stepType === "TOOL_CALL" && step.toolName === "kb.readiness.check") {
    return <ReadinessInsight input={input} output={output} />;
  }
  if (step.stepType === "TOOL_CALL" && step.toolName === "qa.retrieve.probe") {
    return <RetrieveProbeInsight input={input} output={output} />;
  }
  if (step.stepType === "TOOL_CALL" && step.toolName === "retrieval.config.inspect") {
    return <RetrievalConfigInsight input={input} output={output} />;
  }
  if (step.nodeName === "final_report") {
    return <FinalReportInsight output={output} />;
  }
  return null;
}

function DecisionInsight({ output }: { output?: JsonObject }) {
  const decision = asObject(output?.decision);
  if (!decision) return null;

  return (
    <Descriptions column={{ xs: 1, md: 2 }} size="small" bordered>
      <Descriptions.Item label="动作">
        <Tag color={decision.action === "FINAL_ANSWER" ? "green" : decision.action === "REQUEST_CONFIRMATION" ? "gold" : "blue"}>
          {stringValue(decision.action)}
        </Tag>
      </Descriptions.Item>
      <Descriptions.Item label="工具">{stringValue(decision.toolName) || "-"}</Descriptions.Item>
      <Descriptions.Item label="原因" span={2}>{stringValue(decision.reason) || "-"}</Descriptions.Item>
      {decision.finalAnswer ? (
        <Descriptions.Item label="最终回答" span={2}>
          <Typography.Paragraph style={{ margin: 0, whiteSpace: "pre-wrap" }}>{stringValue(decision.finalAnswer)}</Typography.Paragraph>
        </Descriptions.Item>
      ) : null}
    </Descriptions>
  );
}

function ReadinessInsight({ input, output }: { input?: JsonObject; output?: JsonObject }) {
  const raw = asObject(output?.raw);
  if (!raw) return null;
  return (
    <Space direction="vertical" size="small" style={{ width: "100%" }}>
      <ToolQuestion input={input} />
      <Descriptions column={{ xs: 1, md: 3 }} size="small" bordered>
        <Descriptions.Item label="问答就绪">{booleanTag(raw.questionAnsweringReady)}</Descriptions.Item>
        <Descriptions.Item label="需重嵌">{booleanTag(raw.reembedRequired, true)}</Descriptions.Item>
        <Descriptions.Item label="索引中">{booleanTag(raw.reembedInProgress, true)}</Descriptions.Item>
        <Descriptions.Item label="知识库状态">{stringValue(raw.knowledgeBaseStatus) || "-"}</Descriptions.Item>
        <Descriptions.Item label="已索引 chunk">{numberValue(raw.indexedChunkCount)}</Descriptions.Item>
        <Descriptions.Item label="已嵌入 chunk">{numberValue(raw.embeddedChunkCount)}</Descriptions.Item>
        <Descriptions.Item label="下一步" span={3}>{stringValue(raw.nextStep) || "-"}</Descriptions.Item>
      </Descriptions>
    </Space>
  );
}

function RetrievalConfigInsight({ input, output }: { input?: JsonObject; output?: JsonObject }) {
  const raw = asObject(output?.raw);
  if (!raw) return null;
  return (
    <Space direction="vertical" size="small" style={{ width: "100%" }}>
      <ToolQuestion input={input} />
      <Descriptions column={{ xs: 1, md: 3 }} size="small" bordered>
        <Descriptions.Item label="默认模式">{stringValue(raw.defaultMode) || "-"}</Descriptions.Item>
        <Descriptions.Item label="默认 TopK">{numberValue(raw.defaultTopK)}</Descriptions.Item>
        <Descriptions.Item label="最大 TopK">{numberValue(raw.maxTopK)}</Descriptions.Item>
        <Descriptions.Item label="Dense 候选">{numberValue(raw.denseCandidateLimit)}</Descriptions.Item>
        <Descriptions.Item label="Keyword 候选">{numberValue(raw.keywordCandidateLimit)}</Descriptions.Item>
        <Descriptions.Item label="Fusion K">{numberValue(raw.fusionK)}</Descriptions.Item>
        <Descriptions.Item label="Keyword 策略">{stringValue(raw.keywordStrategy) || "-"}</Descriptions.Item>
        <Descriptions.Item label="最小词长">{numberValue(raw.keywordMinTokenLength)}</Descriptions.Item>
        <Descriptions.Item label="最小命中阈值">{numberValue(raw.keywordMinHitThreshold)}</Descriptions.Item>
      </Descriptions>
    </Space>
  );
}

function RetrieveProbeInsight({ input, output }: { input?: JsonObject; output?: JsonObject }) {
  const raw = asObject(output?.raw);
  const dense = asObject(raw?.dense);
  const hybrid = asObject(raw?.hybrid);
  const signals = asObject(raw?.signals);
  if (!raw || !dense || !hybrid) return null;

  const rows: MetricRow[] = [
    { key: "hitCount", label: "最终命中", dense: dense.hitCount, hybrid: hybrid.hitCount },
    { key: "denseHitCount", label: "Dense 命中", dense: dense.denseHitCount, hybrid: hybrid.denseHitCount },
    { key: "keywordHitCount", label: "Keyword 命中", dense: dense.keywordHitCount, hybrid: hybrid.keywordHitCount },
    { key: "fusionStrategy", label: "融合策略", dense: dense.fusionStrategy, hybrid: hybrid.fusionStrategy },
    { key: "totalDurationMs", label: "总耗时", dense: durationValue(dense.totalDurationMs), hybrid: durationValue(hybrid.totalDurationMs) }
  ];

  return (
    <Space direction="vertical" size="small" style={{ width: "100%" }}>
      <ToolQuestion input={input} />
      <Space wrap>
        <Tag icon={<ExperimentOutlined />} color="blue">TopK {numberValue(raw.topK)}</Tag>
        {signalTag("Dense 非空", signals?.denseEmpty === false)}
        {signalTag("Hybrid 非空", signals?.hybridEmpty === false)}
        {signalTag("Keyword 有命中", signals?.keywordZeroHit === false)}
        {signalTag("Hybrid 有增益", signals?.hybridNoGain === false)}
        {signalTag("Top 来源变化", signals?.topSourceChanged === true)}
      </Space>
      <Table<MetricRow>
        size="small"
        rowKey="key"
        columns={probeColumns}
        dataSource={rows}
        pagination={false}
      />
    </Space>
  );
}

function FinalReportInsight({ output }: { output?: JsonObject }) {
  const summary = stringValue(output?.summary);
  if (!summary) return null;
  return (
    <Alert
      type="success"
      showIcon
      icon={<CheckCircleOutlined />}
      message="最终结论"
      description={<Typography.Paragraph style={{ margin: 0, whiteSpace: "pre-wrap" }}>{summary}</Typography.Paragraph>}
    />
  );
}

function ToolQuestion({ input }: { input?: JsonObject }) {
  const originalQuestion = stringValue(input?.originalQuestion);
  const toolQuestion = stringValue(input?.toolQuestion);
  const argumentsObject = asObject(input?.arguments);
  return (
    <Space wrap>
      {argumentsObject?.kbCode ? <Tag color="geekblue">kbCode {stringValue(argumentsObject.kbCode)}</Tag> : null}
      {originalQuestion ? <Tag icon={<BranchesOutlined />}>原问题：{originalQuestion}</Tag> : null}
      {toolQuestion ? <Tag icon={<SettingOutlined />} color={toolQuestion === originalQuestion ? undefined : "purple"}>工具问题：{toolQuestion}</Tag> : null}
    </Space>
  );
}

const probeColumns: ColumnsType<MetricRow> = [
  { title: "指标", dataIndex: "label", key: "label", width: 140 },
  { title: "Dense", dataIndex: "dense", key: "dense", render: renderMetricValue },
  { title: "Hybrid", dataIndex: "hybrid", key: "hybrid", render: renderMetricValue }
];

function renderMetricValue(value: unknown) {
  if (value === undefined || value === null || value === "") return "-";
  return String(value);
}

function parseJsonObject(value?: string): JsonObject | undefined {
  if (!value) return undefined;
  try {
    const parsed = JSON.parse(value) as unknown;
    return asObject(parsed);
  } catch {
    return undefined;
  }
}

function asObject(value: unknown): JsonObject | undefined {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as JsonObject) : undefined;
}

function stringValue(value: unknown) {
  if (value === undefined || value === null) return "";
  return String(value);
}

function numberValue(value: unknown) {
  if (typeof value === "number") return value;
  if (typeof value === "string" && value.trim()) return value;
  return "-";
}

function durationValue(value: unknown) {
  const number = numberValue(value);
  return number === "-" ? "-" : `${number} ms`;
}

function booleanTag(value: unknown, dangerWhenTrue = false) {
  const isTrue = value === true;
  const color = isTrue ? (dangerWhenTrue ? "red" : "green") : dangerWhenTrue ? "green" : "default";
  return <Tag color={color}>{isTrue ? "是" : "否"}</Tag>;
}

function signalTag(label: string, active: boolean) {
  return <Tag color={active ? "green" : "default"}>{label}</Tag>;
}
