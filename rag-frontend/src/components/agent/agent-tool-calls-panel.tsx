import { Alert, Empty, Space, Tag, Typography } from "antd";
import type { AgentToolCallItem } from "./agent-run-view-model";

type AgentToolCallsPanelProps = {
  tools: AgentToolCallItem[];
};

/** 展示工具调用摘要；raw arguments/raw result 统一留在 DebugPanel。 */
export function AgentToolCallsPanel({ tools }: AgentToolCallsPanelProps) {
  if (tools.length === 0) {
    return <Empty description="暂无工具调用" />;
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      {tools.map((tool) => (
        <div key={tool.id} style={{ borderBottom: "1px solid rgba(5, 5, 5, 0.06)", paddingBottom: 12 }}>
          <Space direction="vertical" size="small" style={{ width: "100%" }}>
            <Space wrap>
              <Typography.Text strong>{tool.toolName}</Typography.Text>
              <Tag color={tool.status === "failed" ? "red" : tool.status === "completed" ? "green" : "blue"}>
                {tool.status === "failed" ? "调用失败" : tool.status === "completed" ? "调用完成" : "调用中"}
              </Tag>
              {tool.durationMs !== undefined ? <Tag>{tool.durationMs} ms</Tag> : null}
            </Space>
            {tool.errorMessage ? <Alert type="error" showIcon message={tool.errorMessage} /> : null}
            {tool.summary ? <Typography.Text type="secondary">{tool.summary}</Typography.Text> : null}
          </Space>
        </div>
      ))}
    </Space>
  );
}
