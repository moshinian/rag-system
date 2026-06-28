import { Collapse, Empty, Space, Tag, Timeline, Typography } from "antd";
import type { AgentDecisionOutcomeStatus, AgentDecisionStatus, AgentDecisionTraceItem } from "./agent-run-view-model";

type AgentDecisionTracePanelProps = {
  decisions: AgentDecisionTraceItem[];
};

const visibleDecisionCount = 5;

/** 展示 Agent 的关键决策摘要，而不是 raw decision JSON。 */
export function AgentDecisionTracePanel({ decisions }: AgentDecisionTracePanelProps) {
  if (decisions.length === 0) {
    return <Empty description="暂无 Agent 决策摘要" />;
  }

  const visible = decisions.slice(-visibleDecisionCount);
  const hidden = decisions.slice(0, -visibleDecisionCount);

  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      <Timeline items={visible.map(toTimelineItem)} />
      {hidden.length > 0 ? (
        <Collapse
          size="small"
          items={[
            {
              key: "all",
              label: `查看全部决策（${decisions.length} 条）`,
              children: <Timeline items={decisions.map(toTimelineItem)} />
            }
          ]}
        />
      ) : null}
    </Space>
  );
}

function toTimelineItem(decision: AgentDecisionTraceItem) {
  return {
    key: decision.id,
    color: decisionColor(decision.outcomeStatus),
    children: (
      <Space direction="vertical" size="small" style={{ width: "100%" }}>
        <Space wrap>
          <Typography.Text strong>{decision.title}</Typography.Text>
          <Tag color="blue">{decision.action}</Tag>
          <Tag color={decisionStatusColor(decision.decisionStatus)}>{decisionStatusText(decision.decisionStatus)}</Tag>
          <Tag color={outcomeStatusColor(decision.outcomeStatus)}>{outcomeStatusText(decision.outcomeStatus)}</Tag>
          {decision.toolName ? <Tag color="geekblue">{decision.toolName}</Tag> : null}
          {decision.riskLevel ? <Tag color={decision.riskLevel === "HIGH" ? "red" : decision.riskLevel === "MEDIUM" ? "orange" : "green"}>{decision.riskLevel}</Tag> : null}
          {decision.durationMs !== undefined ? <Tag>Planner 决策耗时 {decision.durationMs} ms</Tag> : null}
          {decision.attemptCount !== undefined ? <Tag>尝试 {decision.attemptCount} 次</Tag> : null}
        </Space>
        {decision.reasonSummary ? <Typography.Text type="secondary">{decision.reasonSummary}</Typography.Text> : null}
        {decision.outcomeSummary ? <Typography.Text>{decision.outcomeSummary}</Typography.Text> : null}
      </Space>
    )
  };
}

function decisionColor(status: AgentDecisionOutcomeStatus) {
  if (status === "failed") return "red";
  if (status === "succeeded" || status === "confirmed") return "green";
  if (status === "waiting_confirmation") return "orange";
  if (status === "running") return "blue";
  return "gray";
}

function decisionStatusText(status: AgentDecisionStatus) {
  const labels: Record<AgentDecisionStatus, string> = {
    generated: "决策已生成",
    active: "执行中",
    resolved: "已收口",
    superseded: "已被后续决策覆盖"
  };
  return labels[status];
}

function decisionStatusColor(status: AgentDecisionStatus) {
  if (status === "resolved") return "green";
  if (status === "active") return "blue";
  if (status === "superseded") return "default";
  return "cyan";
}

function outcomeStatusText(status: AgentDecisionOutcomeStatus) {
  const labels: Record<AgentDecisionOutcomeStatus, string> = {
    pending: "等待后续动作",
    running: "后续动作执行中",
    succeeded: "后续动作成功",
    failed: "后续动作失败",
    waiting_confirmation: "等待人工确认",
    confirmed: "已确认",
    rejected: "已拒绝",
    unknown: "结果未知"
  };
  return labels[status];
}

function outcomeStatusColor(status: AgentDecisionOutcomeStatus) {
  if (status === "failed") return "red";
  if (status === "succeeded" || status === "confirmed") return "green";
  if (status === "waiting_confirmation") return "gold";
  if (status === "rejected") return "default";
  if (status === "running") return "blue";
  return "default";
}
