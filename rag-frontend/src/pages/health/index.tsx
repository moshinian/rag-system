import { useQuery } from "@tanstack/react-query";
import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { getHealthStatus, getRedisProbe } from "../../api/health";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import type { HealthComponentStatus } from "../../types/health";
import { formatDateTime } from "../../utils/format";

type HealthComponentRow = HealthComponentStatus & {
  name: string;
};

const componentColumns: ColumnsType<HealthComponentRow> = [
  {
    title: "组件",
    dataIndex: "name",
    render: (value: string, record) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{value}</Typography.Text>
        <Typography.Text type="secondary">{record.category}</Typography.Text>
      </Space>
    )
  },
  {
    title: "状态",
    dataIndex: "status",
    render: (status: string) => <Tag color={status === "UP" ? "success" : "error"}>{status}</Tag>
  },
  {
    title: "Provider / Model",
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Typography.Text>{record.provider ?? "-"}</Typography.Text>
        <Typography.Text type="secondary">{record.model ?? "-"}</Typography.Text>
      </Space>
    )
  },
  {
    title: "Endpoint",
    dataIndex: "endpoint",
    render: (value: string | null) => (
      <Typography.Text code style={{ fontSize: 12 }}>
        {value ?? "-"}
      </Typography.Text>
    )
  },
  {
    title: "耗时",
    dataIndex: "latencyMs",
    render: (value: number | null) => (value == null ? "-" : `${value} ms`)
  },
  {
    title: "结果",
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Typography.Text>{record.detail ?? "-"}</Typography.Text>
        {record.errorMessage ? (
          <Typography.Text type="danger">{record.errorMessage}</Typography.Text>
        ) : null}
      </Space>
    )
  }
];

/** 渲染页面内容。 */
export function HealthPage() {
  const healthQuery = useQuery({
    queryKey: ["health"],
    queryFn: getHealthStatus
  });

  const redisQuery = useQuery({
    queryKey: ["redisProbe"],
    queryFn: getRedisProbe
  });

  const componentRows: HealthComponentRow[] = Object.entries(healthQuery.data?.components ?? {}).map(
    ([name, component]) => ({
      name,
      ...component
    })
  );

  const downComponents = componentRows.filter((component) => component.status !== "UP");

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      {healthQuery.error ? <ApiErrorAlert error={healthQuery.error} /> : null}
      {redisQuery.error ? <ApiErrorAlert error={redisQuery.error} /> : null}
      {downComponents.length > 0 ? (
        <Alert
          type="warning"
          showIcon
          message="基础能力存在异常"
          description={`当前不可用组件：${downComponents.map((component) => component.name).join(", ")}`}
        />
      ) : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="服务状态" value={healthQuery.data?.status ?? "-"} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="Redis 读写探针" value={redisQuery.data?.matched ? "OK" : "FAILED"} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="检查时间" value={formatDateTime(healthQuery.data?.checkedAt)} />
          </Card>
        </Col>
      </Row>
      <Card title="服务概览">
        <Descriptions column={1}>
          <Descriptions.Item label="服务名">{healthQuery.data?.serviceName}</Descriptions.Item>
          <Descriptions.Item label="Profiles">
            {healthQuery.data?.activeProfiles?.join(", ") || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="Redis Probe Key">{redisQuery.data?.key ?? "-"}</Descriptions.Item>
          <Descriptions.Item label="Redis 写入值">{redisQuery.data?.writtenValue ?? "-"}</Descriptions.Item>
          <Descriptions.Item label="Redis 读取值">{redisQuery.data?.cachedValue ?? "-"}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card title="组件明细">
        <Table
          rowKey="name"
          pagination={false}
          dataSource={componentRows}
          columns={componentColumns}
        />
      </Card>
    </Space>
  );
}
