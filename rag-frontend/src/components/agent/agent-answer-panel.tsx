import { Alert, Space, Tag, Typography } from "antd";
import type { AgentRunViewModel } from "./agent-run-view-model";

type AgentAnswerPanelProps = {
  answer: AgentRunViewModel["answer"];
};

/** 展示用户真正需要阅读的 Agent 结论。 */
export function AgentAnswerPanel({ answer }: AgentAnswerPanelProps) {
  const type = answer.status === "failed" ? "error" : answer.status === "waiting_confirmation" ? "warning" : "success";
  const message =
    answer.status === "failed"
      ? "运行失败"
      : answer.status === "waiting_confirmation"
        ? "等待人工确认"
        : answer.status === "running"
          ? "正在生成结论"
          : "诊断结论";

  return (
    <Alert
      type={type}
      showIcon
      message={
        <Space wrap>
          <span>{message}</span>
          {answer.runCode ? <Tag>{answer.runCode}</Tag> : null}
        </Space>
      }
      description={
        <Typography.Paragraph style={{ margin: 0, whiteSpace: "pre-wrap" }}>
          {answer.errorMessage || answer.text || "暂无可展示结论。"}
        </Typography.Paragraph>
      }
    />
  );
}
