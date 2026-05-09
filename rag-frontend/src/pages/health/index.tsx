import { useQuery } from "@tanstack/react-query";
import { Card, Col, Descriptions, Row, Statistic, Space, Table } from "antd";
import { getHealthStatus, getRedisProbe } from "../../api/health";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { formatDateTime } from "../../utils/format";

export function HealthPage() {
  const healthQuery = useQuery({
    queryKey: ["health"],
    queryFn: getHealthStatus
  });
  const redisQuery = useQuery({
    queryKey: ["redisProbe"],
    queryFn: getRedisProbe
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      {healthQuery.error ? <ApiErrorAlert error={healthQuery.error} /> : null}
      {redisQuery.error ? <ApiErrorAlert error={redisQuery.error} /> : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card><Statistic title="服务状态" value={healthQuery.data?.status ?? "-"} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Redis Probe" value={redisQuery.data?.matched ? "OK" : "FAILED"} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="检查时间" value={formatDateTime(healthQuery.data?.checkedAt)} /></Card>
        </Col>
      </Row>
      <Card title="健康详情">
        <Descriptions column={1}>
          <Descriptions.Item label="服务名">{healthQuery.data?.serviceName}</Descriptions.Item>
          <Descriptions.Item label="Profiles">
            {healthQuery.data?.activeProfiles?.join(", ")}
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card title="组件状态">
        <Table
          rowKey="name"
          pagination={false}
          dataSource={Object.entries(healthQuery.data?.components ?? {}).map(([name, status]) => ({
            name,
            status
          }))}
          columns={[
            { title: "组件", dataIndex: "name" },
            { title: "状态", dataIndex: "status" }
          ]}
        />
      </Card>
    </Space>
  );
}
