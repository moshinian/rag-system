import { Card, Collapse, Empty, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { AgentAction, AgentRun, AgentStep } from "../../types/agent";
import { formatDateTime, truncateText } from "../../utils/format";
import { AgentRunEventTimeline } from "./agent-run-event-timeline";
import type { AgentRunViewModel } from "./agent-run-view-model";

type AgentDebugPanelProps = {
  viewModel: AgentRunViewModel;
  run: AgentRun;
  connectionError?: string;
};

/** 调试区只渲染脱敏后的 SSE 事件和 payload。 */
export function AgentDebugPanel({ viewModel, run, connectionError }: AgentDebugPanelProps) {
  return (
    <Collapse
      items={[
        {
          key: "debug",
          label: "调试详情",
          children: (
            <Space direction="vertical" size="large" style={{ width: "100%" }}>
              <Card size="small" title="原始 Agent Timeline（已脱敏）">
                <AgentRunEventTimeline
                  events={viewModel.debug.sanitizedEvents}
                  connectionStatus={viewModel.progress.connectionStatus}
                  connectionError={connectionError}
                />
              </Card>
              <Card size="small" title="Payload（已脱敏）">
                <PayloadList payloads={viewModel.debug.sanitizedPayloadByEventId} />
              </Card>
              <Card size="small" title="Payload 解析问题">
                {viewModel.debug.parseErrors.length === 0 ? (
                  <Empty description="暂无解析问题" />
                ) : (
                  <Space direction="vertical" style={{ width: "100%" }}>
                    {viewModel.debug.parseErrors.map((error) => (
                      <Typography.Text key={error.eventId} type="danger">
                        {error.eventId}: {error.message}
                      </Typography.Text>
                    ))}
                  </Space>
                )}
              </Card>
              <Card size="small" title="原始 Steps">
                <Table<AgentStep>
                  rowKey="stepCode"
                  columns={stepColumns}
                  dataSource={run.steps}
                  pagination={false}
                  scroll={{ x: 900 }}
                  locale={{ emptyText: "暂无 steps" }}
                />
              </Card>
              <Card size="small" title="原始 Actions">
                <Table<AgentAction>
                  rowKey="actionCode"
                  columns={actionColumns}
                  dataSource={run.actions}
                  pagination={false}
                  scroll={{ x: 1000 }}
                  locale={{ emptyText: "暂无 actions" }}
                />
              </Card>
            </Space>
          )
        }
      ]}
    />
  );
}

function PayloadList({ payloads }: { payloads: Record<string, unknown> }) {
  const entries = Object.entries(payloads);
  if (entries.length === 0) {
    return <Empty description="暂无 payload" />;
  }
  return (
    <Collapse
      size="small"
      items={entries.map(([eventId, payload]) => ({
        key: eventId,
        label: eventId,
        children: (
          <pre style={{ margin: 0, maxHeight: 320, overflow: "auto", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
            {JSON.stringify(payload, null, 2)}
          </pre>
        )
      }))}
    />
  );
}

const stepColumns: ColumnsType<AgentStep> = [
  { title: "节点", dataIndex: "nodeName", key: "nodeName", render: (value) => value || "-" },
  { title: "工具", dataIndex: "toolName", key: "toolName", render: (value) => value || "-" },
  { title: "类型", dataIndex: "stepType", key: "stepType", render: (value) => <Tag>{value}</Tag> },
  { title: "状态", dataIndex: "status", key: "status", render: renderStatus },
  { title: "耗时", dataIndex: "durationMs", key: "durationMs", render: (value) => (value === undefined ? "-" : `${value} ms`) },
  { title: "错误", dataIndex: "errorMessage", key: "errorMessage", render: (value) => truncateText(value, 80) }
];

const actionColumns: ColumnsType<AgentAction> = [
  { title: "动作编码", dataIndex: "actionCode", key: "actionCode", render: (value) => value || "-" },
  { title: "工具", dataIndex: "toolName", key: "toolName", render: (value) => value || "-" },
  { title: "标题", dataIndex: "title", key: "title", render: (value) => value || "-" },
  { title: "风险", dataIndex: "riskLevel", key: "riskLevel", render: renderRisk },
  { title: "状态", dataIndex: "status", key: "status", render: renderStatus },
  { title: "确认时间", dataIndex: "confirmedAt", key: "confirmedAt", render: (value) => formatDateTime(value) },
  { title: "错误", dataIndex: "errorMessage", key: "errorMessage", render: (value) => truncateText(value, 80) }
];

function renderStatus(status: string) {
  const color = status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "REJECTED" ? "default" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function renderRisk(risk: string) {
  const color = risk === "HIGH" ? "red" : risk === "MEDIUM" ? "orange" : "green";
  return <Tag color={color}>{risk}</Tag>;
}
