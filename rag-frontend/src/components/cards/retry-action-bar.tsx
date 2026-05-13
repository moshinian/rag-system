import { Alert, Button, Card, Space, Typography } from "antd";
import type { DocumentIndexingTask } from "../../types/document";

type RetryActionBarProps = {
  task?: DocumentIndexingTask;
  loading?: boolean;
  onRetry: () => void;
};

/** 渲染界面组件。 */
export function RetryActionBar({ task, loading, onRetry }: RetryActionBarProps) {
  if (!task || task.status !== "FAILED") return null;

  /** 渲染界面组件。 */
  const exceeded = (task.retryCount ?? 0) >= (task.maxRetryCount ?? 0);

  return (
    <Card>
      <Space direction="vertical" style={{ width: "100%" }}>
        <Alert
          type="error"
          showIcon
          message="索引失败"
          description={task.errorMessage ?? "未返回详细错误信息"}
        />
        <Space style={{ justifyContent: "space-between", width: "100%" }}>
          <Typography.Text type="secondary">
            最近心跳: {task.lastHeartbeatAt ?? "-"}
          </Typography.Text>
          <Button type="primary" danger disabled={exceeded} loading={loading} onClick={onRetry}>
            {exceeded ? "已达最大重试次数" : "重试索引"}
          </Button>
        </Space>
      </Space>
    </Card>
  );
}
