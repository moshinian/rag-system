import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  Row,
  Space,
  Table,
  Typography
} from "antd";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createKnowledgeBase, listKnowledgeBases } from "../../api/knowledge-base";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { StatusBadge } from "../../components/status/status-badge";
import type { CreateKnowledgeBasePayload } from "../../types/knowledge-base";
import { formatDateTime, truncateText } from "../../utils/format";

export function KnowledgeBasesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ["knowledgeBases", "page"],
    queryFn: () => listKnowledgeBases({ pageNo: 1, pageSize: 100 })
  });

  const createMutation = useMutation({
    mutationFn: createKnowledgeBase,
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["knowledgeBases"] });
      setOpen(false);
      navigate(`/kb/${created.kbCode}`);
    }
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Card>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} md={16}>
            <Typography.Title level={2} style={{ marginBottom: 8 }}>
              企业知识库工作台
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              从知识库创建、文档接入、异步索引到问答回溯，按步骤完成一个可演示的 RAG 闭环。
            </Typography.Paragraph>
          </Col>
          <Col>
            <Button type="primary" size="large" onClick={() => setOpen(true)}>
              创建知识库
            </Button>
          </Col>
        </Row>
      </Card>

      {error ? <ApiErrorAlert error={error} /> : null}

      <Card title="知识库列表">
        <Table
          rowKey="kbCode"
          loading={isLoading}
          dataSource={data?.records}
          pagination={false}
          columns={[
            {
              title: "知识库",
              dataIndex: "name",
              render: (_, record) => (
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{record.name}</Typography.Text>
                  <Typography.Text type="secondary">{record.kbCode}</Typography.Text>
                </Space>
              )
            },
            {
              title: "描述",
              dataIndex: "description",
              render: (value: string) => truncateText(value, 40)
            },
            {
              title: "状态",
              dataIndex: "status",
              render: (value: string) => <StatusBadge type="knowledgeBase" status={value} />
            },
            {
              title: "更新时间",
              dataIndex: "updatedAt",
              render: (value: string) => formatDateTime(value)
            },
            {
              title: "操作",
              render: (_, record) => (
                <Button type="link">
                  <Link to={`/kb/${record.kbCode}`}>进入工作台</Link>
                </Button>
              )
            }
          ]}
        />
      </Card>

      <Drawer title="创建知识库" open={open} onClose={() => setOpen(false)} width={480}>
        <Form<CreateKnowledgeBasePayload> layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item name="kbCode" label="知识库编码" rules={[{ required: true }]}>
            <Input placeholder="例如: finance-kb" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例如: 财务结算知识库" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="createdBy" label="创建人">
            <Input placeholder="frontend-user" />
          </Form.Item>
          {createMutation.error ? <ApiErrorAlert error={createMutation.error} /> : null}
          <Button type="primary" htmlType="submit" loading={createMutation.isPending} block>
            创建并进入
          </Button>
        </Form>
      </Drawer>
    </Space>
  );
}
