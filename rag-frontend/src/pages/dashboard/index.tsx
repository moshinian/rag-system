import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Card, Col, List, Popconfirm, Row, Space, Statistic, Tag, Typography } from "antd";
import { Link } from "react-router-dom";
import {
  disableKnowledgeBase,
  enableKnowledgeBase,
  getKnowledgeBase,
  submitEmbeddingRebuild
} from "../../api/knowledge-base";
import { getReadiness, listDocuments } from "../../api/document";
import { ReadinessCard } from "../../components/cards/readiness-card";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { StatusBadge } from "../../components/status/status-badge";
import { WizardStepper } from "../../components/wizard/wizard-stepper";
import { useCurrentKb } from "../../hooks/use-current-kb";

/** 渲染页面内容。 */
export function DashboardPage() {
  const { message } = App.useApp();

  const queryClient = useQueryClient();

  const kbCode = useCurrentKb();

  const kbQuery = useQuery({
    queryKey: ["knowledgeBase", kbCode],
    queryFn: () => getKnowledgeBase(kbCode!),
    enabled: !!kbCode
  });

  const readinessQuery = useQuery({
    queryKey: ["readiness", kbCode],
    queryFn: () => getReadiness(kbCode!),
    enabled: !!kbCode
  });

  const docsQuery = useQuery({
    queryKey: ["documents", kbCode, "dashboard"],
    queryFn: () => listDocuments(kbCode!, { pageNo: 1, pageSize: 100 }),
    enabled: !!kbCode
  });

  const failedDocsQuery = useQuery({
    queryKey: ["documents", kbCode, "FAILED", "dashboard"],
    queryFn: () => listDocuments(kbCode!, { status: "FAILED", pageNo: 1, pageSize: 6 }),
    enabled: !!kbCode
  });

  const disabledDocsQuery = useQuery({
    queryKey: ["documents", kbCode, "DISABLED", "dashboard"],
    queryFn: () => listDocuments(kbCode!, { status: "DISABLED", pageNo: 1, pageSize: 1 }),
    enabled: !!kbCode
  });

  const disableMutation = useMutation({
    mutationFn: disableKnowledgeBase,
    onSuccess: (_, targetKbCode) => {
      queryClient.invalidateQueries({ queryKey: ["knowledgeBase", targetKbCode] });
      queryClient.invalidateQueries({ queryKey: ["knowledgeBases"] });
      queryClient.invalidateQueries({ queryKey: ["readiness", targetKbCode] });
      message.success("知识库已禁用，当前数据仍保留。");
    }
  });

  const enableMutation = useMutation({
    mutationFn: ({ retryFailedIndexingTasks }: { retryFailedIndexingTasks: boolean }) =>
      enableKnowledgeBase(kbCode!, {
        retryFailedIndexingTasks,
        operator: "frontend-dashboard"
      }),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ["knowledgeBase", response.kbCode] });
      queryClient.invalidateQueries({ queryKey: ["knowledgeBases"] });
      queryClient.invalidateQueries({ queryKey: ["readiness", response.kbCode] });
      queryClient.invalidateQueries({ queryKey: ["documents", response.kbCode] });

      const summary = response.retryFailedIndexingTasks
        ? `并提交 ${response.retriedFailedTaskCount} 个失败任务重试`
        : "未触发失败任务重试";
      message.success(`知识库已恢复使用，${summary}。`);
    }
  });

  const rebuildMutation = useMutation({
    mutationFn: () => submitEmbeddingRebuild("frontend-dashboard"),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ["readiness"] });
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      queryClient.invalidateQueries({ queryKey: ["documentChunks"] });
      message.success(`已提交全局重新嵌入任务 #${response.rebuildRunId}，会影响所有活跃知识库。`);
    }
  });

  if (!kbCode) {
    return <Alert type="warning" showIcon message="请选择知识库" />;
  }

  const failedDocuments = failedDocsQuery.data?.records.map((document) => document.documentCode) ?? [];

  const failedDocumentsCount = failedDocsQuery.data?.total ?? 0;

  const disabledDocumentsCount = disabledDocsQuery.data?.total ?? 0;

  const kbInactive = kbQuery.data?.status === "INACTIVE";

  const reembedRequired = readinessQuery.data?.reembedRequired ?? false;

  const reembedInProgress = readinessQuery.data?.reembedInProgress ?? false;

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <WizardStepper current={0} />
      {kbQuery.error ? <ApiErrorAlert error={kbQuery.error} /> : null}
      {readinessQuery.error ? <ApiErrorAlert error={readinessQuery.error} /> : null}
      <Card>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} md={16}>
            <Typography.Title level={3} style={{ marginBottom: 8 }}>
              {kbQuery.data?.name ?? kbCode}
            </Typography.Title>
            <Space size="middle">
              <StatusBadge
                type="knowledgeBase"
                status={kbQuery.data?.status ?? "INACTIVE"}
              />
              {kbInactive ? <Tag color="default">手工禁用中</Tag> : <Tag color="green">可恢复运营</Tag>}
              <Typography.Text type="secondary">{kbQuery.data?.description}</Typography.Text>
            </Space>
          </Col>
          <Col>
            <Space>
              <Button type="default">
                <Link to={`/kb/${kbCode}/documents`}>查看文档</Link>
              </Button>
              <Button type="primary">
                <Link to={`/kb/${kbCode}/upload`}>上传文档</Link>
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>
      {kbInactive ? (
        <Alert
          type="warning"
          showIcon
          message="当前知识库处于手工禁用状态"
          description="现有实现不会因为切片或 embedding 失败自动禁用知识库。恢复使用后，可以选择是否顺手重试最近一次失败的索引任务。"
        />
      ) : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card><Statistic title="文档数" value={docsQuery.data?.total ?? 0} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic
              title="可检索已切块"
              value={readinessQuery.data?.indexedChunkCount ?? 0}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic
              title="可检索已向量化"
              value={readinessQuery.data?.embeddedChunkCount ?? 0}
            />
          </Card>
        </Col>
      </Row>
      {disabledDocumentsCount > 0 ? (
        <Alert
          type="info"
          showIcon
          message="首页切块/向量统计只计算当前可参与检索的文档"
          description="已禁用文档即使保留历史 chunk 和向量，也不会计入“可检索已切块 / 可检索已向量化”这两个数字。"
        />
      ) : null}
      <Card title="知识库操作">
        <Row gutter={[24, 24]} align="top">
          <Col xs={24} xl={15}>
            <Space direction="vertical" size="middle" style={{ width: "100%" }}>
              <Typography.Text type="secondary">
                这里既包含当前知识库的运维操作，也包含一次会影响所有活跃知识库的全局重嵌入动作。
              </Typography.Text>
              <Space wrap>
                {kbInactive ? (
                  <>
                    <Button
                      type="primary"
                      loading={
                        enableMutation.isPending &&
                        !enableMutation.variables?.retryFailedIndexingTasks
                      }
                      onClick={() => enableMutation.mutate({ retryFailedIndexingTasks: false })}
                    >
                      恢复使用
                    </Button>
                    <Button
                      loading={
                        enableMutation.isPending &&
                        !!enableMutation.variables?.retryFailedIndexingTasks
                      }
                      onClick={() => enableMutation.mutate({ retryFailedIndexingTasks: true })}
                    >
                      恢复并重试失败任务
                    </Button>
                  </>
                ) : (
                  <Popconfirm
                    title="禁用知识库"
                    description="禁用后问答和检索会被就绪门禁阻断，但数据不会删除。"
                    okText="确认禁用"
                    cancelText="取消"
                    onConfirm={() => disableMutation.mutate(kbCode)}
                  >
                    <Button loading={disableMutation.isPending}>禁用知识库</Button>
                  </Popconfirm>
                )}
                <Button
                  type={reembedRequired ? "primary" : "default"}
                  danger={reembedRequired}
                  disabled={reembedInProgress}
                  loading={rebuildMutation.isPending}
                  onClick={() => rebuildMutation.mutate()}
                >
                  {reembedInProgress ? "全局重新嵌入进行中" : "提交全局重新嵌入"}
                </Button>
                <Button type="default">
                  <Link to={`/kb/${kbCode}/history`}>查看问答记录</Link>
                </Button>
              </Space>
              {failedDocumentsCount > 0 ? (
                <Alert
                  type="warning"
                  showIcon
                  message="存在失败文档"
                  description={`最近发现失败文档: ${failedDocuments.join(", ")}${failedDocumentsCount > failedDocuments.length ? " 等" : ""}`}
                />
              ) : null}
            </Space>
          </Col>
          <Col xs={24} xl={9}>
            <Card size="small" title="运行摘要">
              <List
                size="small"
                dataSource={[
                  `失败文档数: ${failedDocumentsCount}`,
                  `已禁用文档数: ${disabledDocumentsCount}`,
                  `需要重新嵌入: ${reembedRequired ? "是" : "否"}`,
                  `当前 Rebuild Run: ${readinessQuery.data?.currentRebuildRunId ?? "-"}`
                ]}
                renderItem={(item) => <List.Item>{item}</List.Item>}
              />
            </Card>
          </Col>
        </Row>
      </Card>
      {readinessQuery.data ? <ReadinessCard kbCode={kbCode} readiness={readinessQuery.data} /> : null}
    </Space>
  );
}
