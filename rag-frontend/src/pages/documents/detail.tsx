import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Card, Col, Descriptions, Row, Space, Typography } from "antd";
import { useParams } from "react-router-dom";
import { retryIndexingTask } from "../../api/document";
import { ChunkPreviewList } from "../../components/cards/chunk-preview-list";
import { IndexingTimeline } from "../../components/cards/indexing-timeline";
import { RetryActionBar } from "../../components/cards/retry-action-bar";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { StatusBadge } from "../../components/status/status-badge";
import { WizardStepper } from "../../components/wizard/wizard-stepper";
import { useCurrentKb } from "../../hooks/use-current-kb";
import { useDocumentMonitor } from "../../hooks/use-polling-task";
import { formatDateTime, formatFileSize } from "../../utils/format";

export function DocumentDetailPage() {
  const kbCode = useCurrentKb();
  const { documentCode } = useParams();
  const queryClient = useQueryClient();
  const monitor = useDocumentMonitor(kbCode!, documentCode!, !!kbCode && !!documentCode);
  const task = monitor.tasksQuery.data?.[0];

  const retryMutation = useMutation({
    mutationFn: () => retryIndexingTask(kbCode!, documentCode!, task!.taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["indexingTasks", kbCode, documentCode] });
      queryClient.invalidateQueries({ queryKey: ["documentDetail", kbCode, documentCode] });
      queryClient.invalidateQueries({ queryKey: ["documentChunks", kbCode, documentCode] });
    }
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <WizardStepper current={2} />
      {monitor.detailQuery.error ? <ApiErrorAlert error={monitor.detailQuery.error} /> : null}
      {monitor.tasksQuery.error ? <ApiErrorAlert error={monitor.tasksQuery.error} /> : null}
      {monitor.chunksQuery.error ? <ApiErrorAlert error={monitor.chunksQuery.error} /> : null}
      <Card loading={monitor.detailQuery.isLoading}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={14}>
            <Typography.Title level={3} style={{ marginTop: 0 }}>
              {monitor.detailQuery.data?.displayName}
            </Typography.Title>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="文档编码">{monitor.detailQuery.data?.documentCode}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {monitor.detailQuery.data ? (
                  <StatusBadge type="document" status={monitor.detailQuery.data.status} />
                ) : "-"}
              </Descriptions.Item>
              <Descriptions.Item label="文件类型">{monitor.detailQuery.data?.fileType}</Descriptions.Item>
              <Descriptions.Item label="大小">
                {formatFileSize(monitor.detailQuery.data?.fileSize)}
              </Descriptions.Item>
              <Descriptions.Item label="来源">{monitor.detailQuery.data?.source ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="标签">{monitor.detailQuery.data?.tags ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {formatDateTime(monitor.detailQuery.data?.updatedAt)}
              </Descriptions.Item>
            </Descriptions>
          </Col>
          <Col xs={24} lg={10}>
            <IndexingTimeline task={task} progress={monitor.progress} />
          </Col>
        </Row>
      </Card>
      <RetryActionBar
        task={task}
        loading={retryMutation.isPending}
        onRetry={() => retryMutation.mutate()}
      />
      <ChunkPreviewList chunks={monitor.chunksQuery.data ?? []} />
    </Space>
  );
}
