import { Badge, Space, Steps, Tag, Typography } from "antd";
import type { AgentProgressPhase, AgentRunViewModel } from "./agent-run-view-model";

type AgentProgressPanelProps = {
  progress: AgentRunViewModel["progress"];
};

const phaseOrder: AgentProgressPhase[] = [
  "started",
  "planning",
  "tooling",
  "summarizing",
  "waiting_confirmation",
  "completed"
];

/** 展示 Agent 当前所处阶段，避免把事件流直接铺成主界面。 */
export function AgentProgressPanel({ progress }: AgentProgressPanelProps) {
  const current = phaseOrder.includes(progress.currentPhase) ? progress.currentPhase : undefined;
  return (
    <Space direction="vertical" size="small" style={{ width: "100%" }}>
      <Space wrap>
        {renderConnectionStatus(progress.connectionStatus)}
        <Tag color={phaseColor(progress.currentPhase)}>{phaseLabel(progress.currentPhase)}</Tag>
        <Typography.Text>{progress.currentLabel}</Typography.Text>
      </Space>
      <Steps
        size="small"
        current={current ? phaseOrder.indexOf(current) : 0}
        status={progress.currentPhase === "failed" ? "error" : "process"}
        items={phaseOrder.map((phase) => ({
          title: phaseLabel(phase),
          status: stepStatus(phase, progress)
        }))}
      />
    </Space>
  );
}

function renderConnectionStatus(status: string) {
  if (status === "OPEN") return <Badge status="success" text="SSE 已连接" />;
  if (status === "RECONNECTING") return <Badge status="warning" text="SSE 重连中" />;
  if (status === "ENDED") return <Badge status="default" text="SSE 已结束" />;
  if (status === "CONNECTING") return <Badge status="processing" text="SSE 连接中" />;
  return <Badge status="default" text="SSE 未连接" />;
}

function phaseLabel(phase: AgentProgressPhase) {
  const labels: Record<AgentProgressPhase, string> = {
    idle: "未开始",
    started: "启动 Agent",
    planning: "分析问题",
    tooling: "调用工具",
    summarizing: "汇总证据",
    waiting_confirmation: "等待确认",
    completed: "已完成",
    failed: "失败"
  };
  return labels[phase];
}

function phaseColor(phase: AgentProgressPhase) {
  if (phase === "completed") return "green";
  if (phase === "failed") return "red";
  if (phase === "waiting_confirmation") return "gold";
  if (phase === "idle") return "default";
  return "blue";
}

function stepStatus(phase: AgentProgressPhase, progress: AgentRunViewModel["progress"]) {
  if (progress.currentPhase === "failed" && progress.seenPhases.includes(phase)) return "error";
  if (phase === progress.currentPhase) return "process";
  if (progress.seenPhases.includes(phase)) return "finish";
  return "wait";
}
