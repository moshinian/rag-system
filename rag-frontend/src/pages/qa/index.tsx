import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Space } from "antd";
import { getReadiness } from "../../api/document";
import { ask } from "../../api/qa";
import { AnswerCard } from "../../components/cards/answer-card";
import { RetrievalResultList } from "../../components/cards/retrieval-result-list";
import { ReadinessCard } from "../../components/cards/readiness-card";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { SourceList } from "../../components/source-viewer/source-list";
import { WizardStepper } from "../../components/wizard/wizard-stepper";
import { useCurrentKb } from "../../hooks/use-current-kb";
import type { RetrievalMode } from "../../types/qa";

type FormValues = {
  question: string;
  topK?: number;
  retrievalMode?: RetrievalMode;
};

/** 渲染页面内容。 */
export function QaPage() {
  const kbCode = useCurrentKb();

  const readinessQuery = useQuery({
    queryKey: ["readiness", kbCode, "qa"],
    queryFn: () => getReadiness(kbCode!),
    enabled: !!kbCode
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => ask(kbCode!, values)
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <WizardStepper current={3} />
      {readinessQuery.data ? <ReadinessCard kbCode={kbCode!} readiness={readinessQuery.data} /> : null}
      <Card title="问答台">
        <Form<FormValues>
          layout="vertical"
          initialValues={{ retrievalMode: "DENSE" }}
          onFinish={(values) => mutation.mutate(values)}
        >
          <Row gutter={16}>
            <Col xs={24} lg={14}>
              <Form.Item name="question" label="问题" rules={[{ required: true }]}>
                <Input.TextArea rows={4} placeholder="例如：结算异常时应如何排查？" />
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
            开始问答
          </Button>
        </Form>
      </Card>
      {mutation.data ? (
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={14}>
            <Space direction="vertical" size="large" style={{ width: "100%" }}>
              <AnswerCard answer={mutation.data} />
              <RetrievalResultList
                items={mutation.data.retrievalResults}
                retrieval={{
                  retrievalMode: mutation.data.retrievalMode,
                  fusionStrategy: mutation.data.fusionStrategy,
                  denseHitCount: mutation.data.denseHitCount,
                  keywordHitCount: mutation.data.keywordHitCount,
                  hitCount: mutation.data.hitCount,
                  denseDurationMs: mutation.data.denseDurationMs,
                  keywordDurationMs: mutation.data.keywordDurationMs,
                  fusionDurationMs: mutation.data.fusionDurationMs,
                  totalDurationMs: mutation.data.totalDurationMs
                }}
              />
            </Space>
          </Col>
          <Col xs={24} xl={10}>
            <Card title="答案来源">
              <SourceList sources={mutation.data.sources} />
            </Card>
          </Col>
        </Row>
      ) : null}
    </Space>
  );
}
