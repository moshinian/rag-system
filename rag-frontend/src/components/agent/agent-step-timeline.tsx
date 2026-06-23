import { Alert, Card, Collapse, Empty, Space, Tag, Timeline, Typography } from "antd";
import type { AgentStep } from "../../types/agent";
import { truncateText } from "../../utils/format";
import { AgentStepInsight } from "./agent-step-insight";

type AgentStepTimelineProps = {
  steps: AgentStep[];
};

/** 展示 Agent 执行轨迹。 */
export function AgentStepTimeline({ steps }: AgentStepTimelineProps) {
  if (steps.length === 0) {
    return <Empty description="暂无执行轨迹" />;
  }

  return (
    <Timeline
      items={steps.map((step) => ({
        key: step.stepCode,
        color: stepColor(step.status),
        children: (
          <Space direction="vertical" size="small" style={{ width: "100%" }}>
            <Space wrap>
              <Typography.Text strong>{step.nodeName || step.stepCode}</Typography.Text>
              <Tag>{step.stepType}</Tag>
              {renderStepStatus(step.status)}
              {step.toolName ? <Tag color="blue">{step.toolName}</Tag> : null}
              <Typography.Text type="secondary">
                {step.durationMs === undefined ? "耗时 -" : `耗时 ${step.durationMs} ms`}
              </Typography.Text>
            </Space>
            {step.errorMessage ? <Alert type="error" showIcon message={step.errorMessage} /> : null}
            <AgentStepInsight step={step} />
            <Collapse
              size="small"
              ghost
              items={[
                step.inputJson
                  ? {
                      key: "input",
                      label: "输入",
                      children: <JsonBlock value={step.inputJson} />
                    }
                  : null,
                step.outputJson
                  ? {
                      key: "output",
                      label: "输出",
                      children: <JsonBlock value={step.outputJson} />
                    }
                  : null
              ].filter((item): item is NonNullable<typeof item> => item !== null)}
            />
          </Space>
        )
      }))}
    />
  );
}

function JsonBlock({ value }: { value: string }) {
  return (
    <Card size="small" styles={{ body: { padding: 12 } }}>
      <pre style={{ margin: 0, maxHeight: 240, overflow: "auto", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
        {prettyJson(value)}
      </pre>
    </Card>
  );
}

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return truncateText(value, 3000);
  }
}

function renderStepStatus(status: string) {
  const color = status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "SKIPPED" ? "default" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function stepColor(status: string) {
  if (status === "SUCCEEDED") return "green";
  if (status === "FAILED") return "red";
  if (status === "SKIPPED") return "gray";
  return "blue";
}
