import { Alert, Button, Card, Col, Descriptions, Progress, Row, Space, Tag, Typography } from "antd";
import { Link } from "react-router-dom";
import type { Readiness } from "../../types/document";

type ReadinessCardProps = {
  kbCode: string;
  readiness: Readiness;
};

/** 渲染复用组件。 */
export function ReadinessCard({ kbCode, readiness }: ReadinessCardProps) {
  const progress =
    readiness.indexedChunkCount > 0
      ? Math.round((readiness.embeddedChunkCount / readiness.indexedChunkCount) * 100)
      : 0;

  return (
    <Card
      title="问答就绪度"
      extra={
        <Space>
          <Button type="primary">
            <Link to={`/kb/${kbCode}/qa`}>进入问答</Link>
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="large" style={{ width: "100%" }}>
        <Alert
          type={readiness.questionAnsweringReady ? "success" : "warning"}
          showIcon
          message={readiness.questionAnsweringReady ? "知识库已可问答" : "知识库尚未就绪"}
          description={readiness.nextStep}
        />
        <Space wrap>
          <Tag color={readiness.questionAnsweringReady ? "green" : "gold"}>
            {readiness.questionAnsweringReady ? "问答可用" : "问答阻断中"}
          </Tag>
          {readiness.reembedRequired ? <Tag color="red">待重新嵌入</Tag> : null}
          {readiness.reembedInProgress ? <Tag color="blue">重新嵌入进行中</Tag> : null}
        </Space>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={14}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Embedding Provider">
                {readiness.embeddingProvider}
              </Descriptions.Item>
              <Descriptions.Item label="Embedding Model">
                {readiness.embeddingModel}
              </Descriptions.Item>
              <Descriptions.Item label="Active Model">
                {readiness.activeEmbeddingModel ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="Vector Store">
                {readiness.vectorStore}
              </Descriptions.Item>
              <Descriptions.Item label="Rebuild Run">
                {readiness.currentRebuildRunId ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="默认 TopK">
                {readiness.defaultTopK}
              </Descriptions.Item>
            </Descriptions>
          </Col>
          <Col xs={24} lg={10}>
            <Typography.Text type="secondary">向量化完成度</Typography.Text>
            <Progress percent={progress} />
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              已切块 {readiness.indexedChunkCount} / 已向量化 {readiness.embeddedChunkCount}
            </Typography.Paragraph>
          </Col>
        </Row>
      </Space>
    </Card>
  );
}
