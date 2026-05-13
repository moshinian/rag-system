import { Alert, Card, Progress, Space, Timeline, Typography } from "antd";
import type { DocumentIndexingTask } from "../../types/document";
import { formatDateTime } from "../../utils/format";
import { getTaskStageLabel } from "../../utils/status";
import { StatusBadge } from "../status/status-badge";

type IndexingTimelineProps = {
  task?: DocumentIndexingTask;
  progress?: number;
};

/** 渲染界面组件。 */
export function IndexingTimeline({ task, progress }: IndexingTimelineProps) {
  if (!task) {
    return (
      <Card title="索引任务">
        <Typography.Text type="secondary">当前文档还没有索引任务。</Typography.Text>
      </Card>
    );
  }

  return (
    <Card
      title="索引任务"
      extra={<StatusBadge type="task" status={task.status} stage={task.taskStage} />}
    >
      <Space direction="vertical" style={{ width: "100%" }} size="large">
        <Timeline
          items={[
            {
              color: task.status === "QUEUED" ? "blue" : "green",
              children: `排队: ${formatDateTime(task.createdAt)}`
            },
            {
              color:
                task.taskStage === "DOCUMENT_PROCESSING" || task.taskStage === "DOCUMENT_EMBEDDING"
                  ? "blue"
                  : task.chunkCount
                    ? "green"
                    : "gray",
              children: `解析切块: ${task.parserName ?? "-"} / chunk ${task.chunkCount ?? 0}`
            },
            {
              color: task.taskStage === "DOCUMENT_EMBEDDING" ? "blue" : task.embeddedChunkCount ? "green" : "gray",
              children: `向量写库: ${task.embeddedChunkCount ?? 0}/${task.chunkCount ?? 0}`
            },
            {
              color: task.status === "SUCCEEDED" ? "green" : task.status === "FAILED" ? "red" : "gray",
              children: `${getTaskStageLabel(task.taskStage)}: ${formatDateTime(task.finishedAt)}`
            }
          ]}
        />
        {progress !== undefined && task.status === "RUNNING" ? <Progress percent={progress} /> : null}
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          触发方式: {task.triggerSource} | 重试次数: {task.retryCount ?? 0}/{task.maxRetryCount ?? 0}
        </Typography.Paragraph>
        {task.errorMessage ? (
          <Alert type="error" showIcon message="最近错误" description={task.errorMessage} />
        ) : null}
      </Space>
    </Card>
  );
}
