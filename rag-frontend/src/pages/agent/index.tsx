import { useMutation } from "@tanstack/react-query";
import { Alert, App, Button, Card, Col, Descriptions, Form, Input, Row, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { confirmAgentAction, createAgentRun, getAgentRun, rejectAgentAction } from "../../api/agent";
import { AgentActionCards } from "../../components/agent/agent-action-cards";
import { AgentStepTimeline } from "../../components/agent/agent-step-timeline";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { useCurrentKb } from "../../hooks/use-current-kb";
import type { AgentAction, AgentRun, AgentRunCreatePayload, AgentRunMode, AgentStep } from "../../types/agent";
import { formatDateTime, truncateText } from "../../utils/format";

type FormValues = {
  goal: string;
  question?: string;
  runMode: AgentRunMode;
  createdBy?: string;
};

const defaultGoal = "诊断这个知识库为什么不能问答";
const failedTaskGoal = "检查这个知识库有没有索引异常";

/** Agent 诊断工作台页面。 */
export function AgentPage() {
  const { message } = App.useApp();
  const kbCode = useCurrentKb();
  const [form] = Form.useForm<FormValues>();
  const [runCodeInput, setRunCodeInput] = useState("");
  const [currentRun, setCurrentRun] = useState<AgentRun>();

  const createMutation = useMutation({
    mutationFn: (values: AgentRunCreatePayload) => createAgentRun(kbCode!, values),
    onSuccess: (run) => {
      setCurrentRun(run);
      setRunCodeInput(run.runCode);
    }
  });

  const queryMutation = useMutation({
    mutationFn: (runCode: string) => getAgentRun(kbCode!, runCode),
    onSuccess: (run) => {
      setCurrentRun(run);
      setRunCodeInput(run.runCode);
    }
  });

  const confirmMutation = useMutation({
    mutationFn: (action: AgentAction) =>
      confirmAgentAction(kbCode!, currentRun!.runCode, action.actionCode, {
        operator: form.getFieldValue("createdBy")?.trim() || "frontend"
      }),
    onSuccess: (run) => {
      setCurrentRun(run);
      setRunCodeInput(run.runCode);
      message.success("推荐动作已确认执行。");
    }
  });

  const rejectMutation = useMutation({
    mutationFn: ({ action, reason }: { action: AgentAction; reason?: string }) =>
      rejectAgentAction(kbCode!, currentRun!.runCode, action.actionCode, {
        operator: form.getFieldValue("createdBy")?.trim() || "frontend",
        reason
      }),
    onSuccess: (run) => {
      setCurrentRun(run);
      setRunCodeInput(run.runCode);
      message.success("推荐动作已拒绝。");
    }
  });

  const stepColumns = useMemo<ColumnsType<AgentStep>>(
    () => [
      { title: "节点", dataIndex: "nodeName", key: "nodeName", render: (value) => value || "-" },
      { title: "工具", dataIndex: "toolName", key: "toolName", render: (value) => value || "-" },
      { title: "类型", dataIndex: "stepType", key: "stepType", render: (value) => <Tag>{value}</Tag> },
      { title: "状态", dataIndex: "status", key: "status", render: (value) => renderStepStatus(value) },
      { title: "耗时", dataIndex: "durationMs", key: "durationMs", render: (value) => (value === undefined ? "-" : `${value} ms`) },
      { title: "错误", dataIndex: "errorMessage", key: "errorMessage", render: (value) => truncateText(value, 80) }
    ],
    []
  );

  const actionColumns = useMemo<ColumnsType<AgentAction>>(
    () => [
      { title: "动作编码", dataIndex: "actionCode", key: "actionCode", render: (value) => value || "-" },
      { title: "工具", dataIndex: "toolName", key: "toolName", render: (value) => value || "-" },
      { title: "标题", dataIndex: "title", key: "title", render: (value) => value || "-" },
      { title: "风险", dataIndex: "riskLevel", key: "riskLevel", render: (value) => renderRisk(value) },
      {
        title: "需确认",
        dataIndex: "requiresConfirmation",
        key: "requiresConfirmation",
        render: (value) => (value ? "是" : "否")
      },
      { title: "状态", dataIndex: "status", key: "status", render: (value) => renderActionStatus(value) },
      { title: "错误", dataIndex: "errorMessage", key: "errorMessage", render: (value) => truncateText(value, 80) }
    ],
    []
  );

  function submit(values: FormValues) {
    createMutation.mutate({
      goal: values.goal,
      question: values.question?.trim() || undefined,
      runMode: values.runMode,
      createdBy: values.createdBy?.trim() || undefined
    });
  }

  function queryRun() {
    const normalized = runCodeInput.trim();
    if (!normalized) return;
    queryMutation.mutate(normalized);
  }

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Card title="Agent 诊断">
        <Form<FormValues>
          form={form}
          layout="vertical"
          initialValues={{
            goal: defaultGoal,
            question: "第二百三十八条是什么",
            runMode: "DIAGNOSE_AND_RECOMMEND",
            createdBy: "frontend"
          }}
          onFinish={submit}
        >
          <Row gutter={16}>
            <Col xs={24} lg={12}>
              <Form.Item name="goal" label="诊断目标" rules={[{ required: true, message: "请输入诊断目标" }]}>
                <Input.TextArea rows={4} placeholder="描述要诊断的知识库问题" />
              </Form.Item>
            </Col>
            <Col xs={24} lg={12}>
              <Form.Item name="question" label="可选问题">
                <Input.TextArea rows={4} placeholder="用于 readiness 或检索探测的问题" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="runMode" label="运行模式">
                <Select
                  options={[
                    { label: "诊断并推荐", value: "DIAGNOSE_AND_RECOMMEND" },
                    { label: "仅诊断", value: "DIAGNOSE_ONLY" }
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="createdBy" label="创建人">
                <Input placeholder="frontend" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="快捷目标">
                <Space wrap>
                  <Button onClick={() => form.setFieldsValue({ goal: defaultGoal })}>问答 readiness</Button>
                  <Button onClick={() => form.setFieldsValue({ goal: failedTaskGoal, question: undefined })}>索引异常</Button>
                </Space>
              </Form.Item>
            </Col>
          </Row>
          {createMutation.error ? <ApiErrorAlert error={createMutation.error} /> : null}
          <Button type="primary" htmlType="submit" loading={createMutation.isPending} disabled={!kbCode}>
            创建诊断 Run
          </Button>
        </Form>
      </Card>

      <Card title="查询 Run">
        <Space.Compact style={{ width: "100%" }}>
          <Input value={runCodeInput} placeholder="输入 runCode，例如 AR-..." onChange={(event) => setRunCodeInput(event.target.value)} />
          <Button onClick={queryRun} loading={queryMutation.isPending} disabled={!kbCode || !runCodeInput.trim()}>
            查询
          </Button>
          <Button onClick={queryRun} loading={queryMutation.isPending} disabled={!kbCode || !currentRun?.runCode}>
            刷新
          </Button>
        </Space.Compact>
        {queryMutation.error ? <div style={{ marginTop: 16 }}><ApiErrorAlert error={queryMutation.error} /></div> : null}
      </Card>

      {currentRun ? (
        <>
          <Card title="运行概览">
            {currentRun.errorMessage ? (
              <Alert type="error" showIcon message={currentRun.errorMessage} style={{ marginBottom: 16 }} />
            ) : null}
            <Descriptions column={{ xs: 1, md: 2, xl: 3 }} bordered size="small">
              <Descriptions.Item label="runCode">{currentRun.runCode}</Descriptions.Item>
              <Descriptions.Item label="知识库">{currentRun.knowledgeBaseCode}</Descriptions.Item>
              <Descriptions.Item label="状态">{renderRunStatus(currentRun.status)}</Descriptions.Item>
              <Descriptions.Item label="模式">{currentRun.runMode}</Descriptions.Item>
              <Descriptions.Item label="创建人">{currentRun.createdBy}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(currentRun.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="完成时间">{formatDateTime(currentRun.finishedAt)}</Descriptions.Item>
              <Descriptions.Item label="目标" span={2}>{currentRun.goal}</Descriptions.Item>
              <Descriptions.Item label="问题" span={2}>{currentRun.question || "-"}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="诊断摘要">
            {currentRun.summary ? (
              <Typography.Paragraph style={{ marginBottom: 0 }}>{currentRun.summary}</Typography.Paragraph>
            ) : (
              <Typography.Text type="secondary">暂无摘要</Typography.Text>
            )}
          </Card>

          <Card title="执行轨迹">
            <AgentStepTimeline steps={currentRun.steps} />
          </Card>

          <Card title="推荐动作">
            <Space direction="vertical" size="middle" style={{ width: "100%" }}>
              {confirmMutation.error ? <ApiErrorAlert error={confirmMutation.error} /> : null}
              {rejectMutation.error ? <ApiErrorAlert error={rejectMutation.error} /> : null}
              <AgentActionCards
                actions={currentRun.actions}
                confirmingActionCode={confirmMutation.variables?.actionCode}
                rejectingActionCode={rejectMutation.variables?.action.actionCode}
                onConfirm={(action) => confirmMutation.mutate(action)}
                onReject={(action, reason) => rejectMutation.mutate({ action, reason })}
              />
            </Space>
          </Card>

          <Card title="原始 Steps">
            <Table<AgentStep>
              rowKey="stepCode"
              columns={stepColumns}
              dataSource={currentRun.steps}
              pagination={false}
              scroll={{ x: 900 }}
              locale={{ emptyText: "暂无 steps" }}
            />
          </Card>

          <Card title="原始 Actions">
            <Table<AgentAction>
              rowKey="actionCode"
              columns={actionColumns}
              dataSource={currentRun.actions}
              pagination={false}
              scroll={{ x: 1000 }}
              locale={{ emptyText: "暂无 actions" }}
            />
          </Card>
        </>
      ) : null}
    </Space>
  );
}

function renderRunStatus(status: string) {
  const color = status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "WAITING_CONFIRMATION" ? "gold" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function renderStepStatus(status: string) {
  const color = status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "SKIPPED" ? "default" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function renderActionStatus(status: string) {
  const color =
    status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "REJECTED" ? "default" : status === "PENDING_CONFIRMATION" ? "gold" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function renderRisk(risk: string) {
  const color = risk === "HIGH" ? "red" : risk === "MEDIUM" ? "orange" : "green";
  return <Tag color={color}>{risk}</Tag>;
}
