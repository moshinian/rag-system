import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Card, Col, Descriptions, Popconfirm, Row, Space, Typography } from "antd";
import { useParams } from "react-router-dom";
import { disableDocument, enableDocument, retryIndexingTask } from "../../api/document";
import { ChunkInspector } from "../../components/cards/chunk-inspector";
import { IndexingTimeline } from "../../components/cards/indexing-timeline";
import { RetryActionBar } from "../../components/cards/retry-action-bar";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { StatusBadge } from "../../components/status/status-badge";
import { WizardStepper } from "../../components/wizard/wizard-stepper";
import { useCurrentKb } from "../../hooks/use-current-kb";
import { useDocumentMonitor } from "../../hooks/use-polling-task";
import { formatDateTime, formatFileSize } from "../../utils/format";

export function DocumentDetailPage() {
  const { message } = App.useApp();
  const kbCode = useCurrentKb();
  const { documentCode } = useParams();
  const queryClient = useQueryClient();
  const monitor = useDocumentMonitor(kbCode!, documentCode!, !!kbCode && !!documentCode);
  const task = monitor.tasksQuery.data?.[0];
  const detail = monitor.detailQuery.data;

  const refreshQueries = () => {
    queryClient.invalidateQueries({ queryKey: ["indexingTasks", kbCode, documentCode] });
    queryClient.invalidateQueries({ queryKey: ["documentDetail", kbCode, documentCode] });
    queryClient.invalidateQueries({ queryKey: ["documentChunks", kbCode, documentCode] });
    queryClient.invalidateQueries({ queryKey: ["documents", kbCode] });
    queryClient.invalidateQueries({ queryKey: ["readiness", kbCode] });
  };

  const retryMutation = useMutation({
    mutationFn: () => retryIndexingTask(kbCode!, documentCode!, task!.taskId),
    onSuccess: () => {
      refreshQueries();
    }
  });
  const disableMutation = useMutation({
    mutationFn: () => disableDocument(kbCode!, documentCode!),
    onSuccess: () => {
      refreshQueries();
      message.success("文档已禁用，当前不会参与检索和问答。");
    }
  });
  const enableMutation = useMutation({
    mutationFn: () => enableDocument(kbCode!, documentCode!),
    onSuccess: () => {
      refreshQueries();
      message.success("文档已恢复。");
    }
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <WizardStepper current={2} />
      {monitor.detailQuery.error ? <ApiErrorAlert error={monitor.detailQuery.error} /> : null}
      {monitor.tasksQuery.error ? <ApiErrorAlert error={monitor.tasksQuery.error} /> : null}
      {monitor.chunksQuery.error ? <ApiErrorAlert error={monitor.chunksQuery.error} /> : null}
      {detail?.status === "DISABLED" ? (
        <Alert
          type="info"
          showIcon
          message="当前文档已禁用"
          description="历史 chunk 和向量仍会保留并展示在本页，但不会计入知识库首页的可检索切块/向量统计，也不会参与问答检索。"
        />
      ) : null}
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
                {formatDateTime(detail?.updatedAt)}
              </Descriptions.Item>
            </Descriptions>
            <Space style={{ marginTop: 16 }}>
              {detail?.status === "DISABLED" ? (
                <Button
                  type="primary"
                  loading={enableMutation.isPending}
                  onClick={() => enableMutation.mutate()}
                >
                  恢复文档
                </Button>
              ) : (
                <Popconfirm
                  title="禁用文档"
                  description="禁用后历史 chunk 和向量会保留，但不会参与检索和问答。"
                  okText="确认禁用"
                  cancelText="取消"
                  onConfirm={() => disableMutation.mutate()}
                >
                  <Button danger loading={disableMutation.isPending}>
                    禁用文档
                  </Button>
                </Popconfirm>
              )}
            </Space>
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
      <ChunkInspector chunks={monitor.chunksQuery.data ?? []} />
    </Space>
  );
}
