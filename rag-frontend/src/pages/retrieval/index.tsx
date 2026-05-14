import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Space } from "antd";
import { getReadiness } from "../../api/document";
import { retrieve } from "../../api/qa";
import { RetrievalResultList } from "../../components/cards/retrieval-result-list";
import { ReadinessCard } from "../../components/cards/readiness-card";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { useCurrentKb } from "../../hooks/use-current-kb";
import type { RetrievalMode } from "../../types/qa";

type FormValues = {
  question: string;
  topK?: number;
  retrievalMode?: RetrievalMode;
};

/** 渲染页面内容。 */
export function RetrievalPage() {
  const kbCode = useCurrentKb();

  const readinessQuery = useQuery({
    queryKey: ["readiness", kbCode, "retrieval"],
    queryFn: () => getReadiness(kbCode!),
    enabled: !!kbCode
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => retrieve(kbCode!, values)
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      {readinessQuery.data ? <ReadinessCard kbCode={kbCode!} readiness={readinessQuery.data} /> : null}
      <Card title="检索调试">
        <Form<FormValues>
          layout="vertical"
          initialValues={{ retrievalMode: "DENSE" }}
          onFinish={(values) => mutation.mutate(values)}
        >
          <Row gutter={16}>
            <Col xs={24} lg={14}>
              <Form.Item name="question" label="问题" rules={[{ required: true }]}>
                <Input.TextArea rows={4} placeholder="输入一个要验证召回质量的问题" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={5}>
              <Form.Item name="topK" label="TopK">
                <InputNumber min={1} max={10} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={5}>
              <Form.Item name="retrievalMode" label="检索模式">
                <Select
                  options={[
                    { label: "Dense", value: "DENSE" },
                    { label: "Hybrid", value: "HYBRID" }
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          {mutation.error ? <ApiErrorAlert error={mutation.error} /> : null}
          <Button type="primary" htmlType="submit" loading={mutation.isPending}>
            执行检索
          </Button>
        </Form>
      </Card>
      {mutation.data ? <RetrievalResultList items={mutation.data.chunks} retrieval={mutation.data} /> : null}
    </Space>
  );
}
