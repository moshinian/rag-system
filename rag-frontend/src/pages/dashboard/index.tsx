import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Row, Space, Statistic, Typography } from "antd";
import { Link } from "react-router-dom";
import { getKnowledgeBase } from "../../api/knowledge-base";
import { getReadiness, listDocuments } from "../../api/document";
import { ReadinessCard } from "../../components/cards/readiness-card";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { StatusBadge } from "../../components/status/status-badge";
import { WizardStepper } from "../../components/wizard/wizard-stepper";
import { useCurrentKb } from "../../hooks/use-current-kb";

export function DashboardPage() {
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
    queryFn: () => listDocuments(kbCode!, { pageNo: 1, pageSize: 5 }),
    enabled: !!kbCode
  });

  if (!kbCode) {
    return <Alert type="warning" showIcon message="请选择知识库" />;
  }

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
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card><Statistic title="文档数" value={docsQuery.data?.total ?? 0} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="已切块" value={readinessQuery.data?.indexedChunkCount ?? 0} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="已向量化" value={readinessQuery.data?.embeddedChunkCount ?? 0} /></Card>
        </Col>
      </Row>
      {readinessQuery.data ? <ReadinessCard kbCode={kbCode} readiness={readinessQuery.data} /> : null}
    </Space>
  );
}
