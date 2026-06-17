import { useQuery } from "@tanstack/react-query";
import { Alert, Card, Col, Descriptions, Divider, Empty, Row, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { getHealthStatus, getRedisProbe } from "../../api/health";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import type { HealthComponentStatus } from "../../types/health";
import { formatDateTime } from "../../utils/format";

type HealthComponentRow = HealthComponentStatus & {
  name: string;
};

type HealthComponentGroup = {
  title: string;
  description: string;
  items: HealthComponentRow[];
};

const LATENCY_WARNING_MS = 1000;

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
    render: (value: number | null) => renderLatency(value)
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

function renderStatusTag(status?: string) {
  if (!status) {
    return <Tag>-</Tag>;
  }
  return <Tag color={status === "UP" ? "success" : "error"}>{status}</Tag>;
}

function renderLatency(value: number | null) {
  if (value == null) {
    return "-";
  }
  if (value >= LATENCY_WARNING_MS) {
    return <Tag color="warning">{value} ms</Tag>;
  }
  return `${value} ms`;
}

function getHealthPriority(component: HealthComponentRow) {
  if (component.status !== "UP") {
    return 0;
  }
  if ((component.latencyMs ?? 0) >= LATENCY_WARNING_MS) {
    return 1;
  }
  return 2;
}

function sortComponents(components: HealthComponentRow[]) {
  return [...components].sort((left, right) => {
    const priorityDiff = getHealthPriority(left) - getHealthPriority(right);
    if (priorityDiff !== 0) {
      return priorityDiff;
    }
    const latencyDiff = (right.latencyMs ?? -1) - (left.latencyMs ?? -1);
    if (latencyDiff !== 0) {
      return latencyDiff;
    }
    return left.name.localeCompare(right.name);
  });
}

function HealthComponentCard({ component }: { component: HealthComponentRow }) {
  return (
    <Card size="small" style={{ height: "100%" }}>
      <Space direction="vertical" size="middle" style={{ width: "100%" }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{component.name}</Typography.Text>
            <Typography.Text type="secondary">{component.category}</Typography.Text>
          </Space>
          {renderStatusTag(component.status)}
        </Space>
        <Descriptions column={1} size="small" styles={{ label: { width: 110 } }}>
          <Descriptions.Item label="Provider">{component.provider ?? "-"}</Descriptions.Item>
          <Descriptions.Item label="Model">{component.model ?? "-"}</Descriptions.Item>
          <Descriptions.Item label="Latency">{renderLatency(component.latencyMs)}</Descriptions.Item>
          <Descriptions.Item label="Checked At">
            {formatDateTime(component.checkedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="Endpoint">
            <Typography.Text code style={{ fontSize: 12 }}>
              {component.endpoint ?? "-"}
            </Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="结果">{component.detail ?? "-"}</Descriptions.Item>
          {component.errorMessage ? (
            <Descriptions.Item label="错误">
              <Typography.Text type="danger">{component.errorMessage}</Typography.Text>
            </Descriptions.Item>
          ) : null}
        </Descriptions>
      </Space>
    </Card>
  );
}

function HealthGroupSection({ group }: { group: HealthComponentGroup }) {
  if (group.items.length === 0) {
    return null;
  }

  return (
    <Card title={group.title} extra={<Typography.Text type="secondary">{group.description}</Typography.Text>}>
      <Row gutter={[16, 16]}>
        {group.items.map((component) => (
          <Col key={component.name} xs={24} xl={group.items.length === 1 ? 24 : 12}>
            <HealthComponentCard component={component} />
          </Col>
        ))}
      </Row>
    </Card>
  );
}

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

  const sortedComponentRows = sortComponents(componentRows);
  const downComponents = sortedComponentRows.filter((component) => component.status !== "UP");
  const slowComponents = sortedComponentRows.filter(
    (component) => component.status === "UP" && (component.latencyMs ?? 0) >= LATENCY_WARNING_MS
  );
  const infrastructureComponents = sortComponents(
    sortedComponentRows.filter((component) => component.category === "infrastructure")
  );
  const aiGatewayComponents = sortComponents(
    sortedComponentRows.filter((component) => component.category === "ai-gateway")
  );
  const aiCapabilityComponents = sortComponents(
    sortedComponentRows.filter((component) => component.category === "ai-capability")
  );
  const groupedSections: HealthComponentGroup[] = [
    {
      title: "基础设施",
      description: "数据库、缓存与最小读写闭环",
      items: infrastructureComponents
    },
    {
      title: "AI Gateway",
      description: "rag-ai-service 自身存活与接口可达性",
      items: aiGatewayComponents
    },
    {
      title: "模型能力",
      description: "通过 rag-ai-service 发起的真实 embedding 与 chat 探针",
      items: aiCapabilityComponents
    }
  ];

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
      {slowComponents.length > 0 ? (
        <Alert
          type="info"
          showIcon
          message="存在慢探针"
          description={`当前高延迟组件：${slowComponents.map((component) => `${component.name} (${component.latencyMs} ms)`).join(", ")}`}
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
            <Statistic title="异常组件数" value={downComponents.length} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="Redis 读写探针" value={redisQuery.data?.matched ? "OK" : "FAILED"} />
          </Card>
        </Col>
      </Row>
      <Card title="服务概览">
        <Descriptions column={1}>
          <Descriptions.Item label="服务名">{healthQuery.data?.serviceName}</Descriptions.Item>
          <Descriptions.Item label="Profiles">
            {healthQuery.data?.activeProfiles?.join(", ") || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="检查时间">{formatDateTime(healthQuery.data?.checkedAt)}</Descriptions.Item>
          <Descriptions.Item label="组件总数">{componentRows.length}</Descriptions.Item>
          <Descriptions.Item label="高延迟组件数">{slowComponents.length}</Descriptions.Item>
          <Descriptions.Item label="Redis Probe Key">{redisQuery.data?.key ?? "-"}</Descriptions.Item>
          <Descriptions.Item label="Redis 写入值">{redisQuery.data?.writtenValue ?? "-"}</Descriptions.Item>
          <Descriptions.Item label="Redis 读取值">{redisQuery.data?.cachedValue ?? "-"}</Descriptions.Item>
        </Descriptions>
      </Card>
      {componentRows.length === 0 ? (
        <Card title="组件状态">
          <Empty description="暂无健康检查数据" />
        </Card>
      ) : (
        groupedSections.map((group) => <HealthGroupSection key={group.title} group={group} />)
      )}
      <Card title="组件明细">
        <Typography.Text type="secondary">
          统一表格视图，便于快速对比 endpoint、耗时和错误详情。
        </Typography.Text>
        <Divider style={{ margin: "16px 0" }} />
        <Table
          rowKey="name"
          pagination={false}
          dataSource={sortedComponentRows}
          columns={componentColumns}
        />
      </Card>
    </Space>
  );
}
