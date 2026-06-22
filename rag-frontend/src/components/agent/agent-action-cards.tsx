import { CheckOutlined, CloseOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Collapse, Descriptions, Empty, Input, Modal, Popconfirm, Space, Tag, Typography } from "antd";
import { useState } from "react";
import type { AgentAction } from "../../types/agent";
import { formatDateTime, truncateText } from "../../utils/format";

type AgentActionCardsProps = {
  actions: AgentAction[];
  confirmingActionCode?: string;
  rejectingActionCode?: string;
  onConfirm?: (action: AgentAction) => void;
  onReject?: (action: AgentAction, reason?: string) => void;
};

/** 展示 Agent 推荐动作。 */
export function AgentActionCards({
  actions,
  confirmingActionCode,
  rejectingActionCode,
  onConfirm,
  onReject
}: AgentActionCardsProps) {
  const [rejectingAction, setRejectingAction] = useState<AgentAction>();
  const [rejectReason, setRejectReason] = useState("");

  if (actions.length === 0) {
    return <Empty description="暂无推荐动作" />;
  }

  function closeRejectModal() {
    setRejectingAction(undefined);
    setRejectReason("");
  }

  function submitReject() {
    if (!rejectingAction) return;
    onReject?.(rejectingAction, rejectReason.trim() || undefined);
    closeRejectModal();
  }

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: "100%" }}>
        {actions.map((action) => {
          const canReview = action.requiresConfirmation && action.status === "PENDING_CONFIRMATION";
          const canConfirm = canReview && action.riskLevel !== "HIGH";
          const confirming = confirmingActionCode === action.actionCode;
          const rejecting = rejectingActionCode === action.actionCode;

          return (
            <Card
              key={action.actionCode}
              title={action.title || action.toolName}
              extra={
                <Space wrap>
                  {renderRisk(action.riskLevel)}
                  {renderActionStatus(action.status)}
                  {action.requiresConfirmation ? <Tag color="gold">需要人工确认</Tag> : <Tag>无需确认</Tag>}
                </Space>
              }
            >
              <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                {action.reason ? <Typography.Paragraph style={{ margin: 0 }}>{action.reason}</Typography.Paragraph> : null}
                {action.errorMessage ? <Alert type="error" showIcon message={action.errorMessage} /> : null}
                {canReview && action.riskLevel === "HIGH" ? (
                  <Alert type="warning" showIcon message="高风险动作需要更高等级人工流程，当前页面不提供直接确认。" />
                ) : null}
                {canReview ? (
                  <Space wrap>
                    <Popconfirm
                      title="确认执行推荐动作？"
                      description="确认后将由 Java 后端按白名单执行业务写操作。"
                      okText="确认执行"
                      cancelText="取消"
                      disabled={!canConfirm || !onConfirm}
                      onConfirm={() => onConfirm?.(action)}
                    >
                      <Button
                        type="primary"
                        icon={<CheckOutlined />}
                        loading={confirming}
                        disabled={!canConfirm || !onConfirm || rejecting}
                      >
                        确认执行
                      </Button>
                    </Popconfirm>
                    <Button
                      icon={<CloseOutlined />}
                      loading={rejecting}
                      disabled={!onReject || confirming}
                      onClick={() => setRejectingAction(action)}
                    >
                      拒绝
                    </Button>
                  </Space>
                ) : null}
                <Descriptions column={{ xs: 1, md: 2, xl: 3 }} size="small" bordered>
                  <Descriptions.Item label="actionCode">{action.actionCode}</Descriptions.Item>
                  <Descriptions.Item label="toolName">{action.toolName}</Descriptions.Item>
                  <Descriptions.Item label="确认人">{action.confirmedBy || "-"}</Descriptions.Item>
                  <Descriptions.Item label="确认时间">{formatDateTime(action.confirmedAt)}</Descriptions.Item>
                  <Descriptions.Item label="执行时间">{formatDateTime(action.executedAt)}</Descriptions.Item>
                  <Descriptions.Item label="创建时间">{formatDateTime(action.createdAt)}</Descriptions.Item>
                </Descriptions>
                <Collapse
                  size="small"
                  items={[
                    action.actionPayload
                      ? {
                          key: "payload",
                          label: "actionPayload",
                          children: <JsonBlock value={action.actionPayload} />
                        }
                      : null,
                    action.resultJson
                      ? {
                          key: "result",
                          label: "resultJson",
                          children: <JsonBlock value={action.resultJson} />
                        }
                      : null
                  ].filter((item): item is NonNullable<typeof item> => item !== null)}
                />
              </Space>
            </Card>
          );
        })}
      </Space>
      <Modal
        title="拒绝推荐动作"
        open={!!rejectingAction}
        okText="确认拒绝"
        cancelText="取消"
        okButtonProps={{ loading: rejectingAction ? rejectingActionCode === rejectingAction.actionCode : false }}
        onOk={submitReject}
        onCancel={closeRejectModal}
      >
        <Space direction="vertical" size="small" style={{ width: "100%" }}>
          <Typography.Text type="secondary">{rejectingAction?.title || rejectingAction?.toolName}</Typography.Text>
          <Input.TextArea
            rows={4}
            value={rejectReason}
            maxLength={500}
            showCount
            placeholder="可选：填写拒绝原因"
            onChange={(event) => setRejectReason(event.target.value)}
          />
        </Space>
      </Modal>
    </>
  );
}

function JsonBlock({ value }: { value: string }) {
  return (
    <pre style={{ margin: 0, maxHeight: 240, overflow: "auto", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
      {prettyJson(value)}
    </pre>
  );
}

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return truncateText(value, 3000);
  }
}

function renderActionStatus(status: string) {
  const color =
    status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "REJECTED" ? "default" : status === "PENDING_CONFIRMATION" ? "gold" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function renderRisk(risk: string) {
  const color = risk === "HIGH" ? "red" : risk === "MEDIUM" ? "orange" : "green";
  return <Tag color={color}>{risk}</Tag>;
}
