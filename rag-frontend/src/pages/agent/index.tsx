import { useMutation } from "@tanstack/react-query";
import { Alert, App, Button, Card, Col, Descriptions, Form, Input, Row, Select, Space, Tag, Typography } from "antd";
import { useCallback, useMemo, useState } from "react";
import { confirmAgentAction, createAgentRun, getAgentRun, rejectAgentAction } from "../../api/agent";
import { AgentActionCards } from "../../components/agent/agent-action-cards";
import { AgentAnswerPanel } from "../../components/agent/agent-answer-panel";
import { AgentDebugPanel } from "../../components/agent/agent-debug-panel";
import { AgentDecisionTracePanel } from "../../components/agent/agent-decision-trace-panel";
import { AgentEvidencePanel } from "../../components/agent/agent-evidence-panel";
import { AgentProgressPanel } from "../../components/agent/agent-progress-panel";
import { AgentToolCallsPanel } from "../../components/agent/agent-tool-calls-panel";
import { buildAgentRunViewModel } from "../../components/agent/agent-run-view-model";
import type { AgentRunViewModel } from "../../components/agent/agent-run-view-model";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { useAgentRunEvents } from "../../hooks/use-agent-run-events";
import { useCurrentKb } from "../../hooks/use-current-kb";
import type { AgentAction, AgentRun, AgentRunCreatePayload, AgentRunMode } from "../../types/agent";
import { formatDateTime } from "../../utils/format";

type FormValues = {
  goal: string;
  question?: string;
  runMode: AgentRunMode;
  createdBy?: string;
};

const defaultGoal = "诊断这个知识库为什么不能问答";
const failedTaskGoal = "检查这个知识库有没有索引异常";
const financeRetrievalGoal = "诊断财务结算知识库对“结算异常怎么处理？”的检索和问答准备情况，比较 Dense/Hybrid 是否有收益，并检查当前检索配置影响";
const financeRetrievalQuestion = "结算异常怎么处理？";

/** Agent 诊断工作台页面。 */
export function AgentPage() {
  const { message } = App.useApp();
  const kbCode = useCurrentKb();
  const [form] = Form.useForm<FormValues>();
  const [runCodeInput, setRunCodeInput] = useState("");
  const [currentRun, setCurrentRun] = useState<AgentRun>();

  const refreshRunDetail = useCallback(
    (runCode: string) => {
      if (!kbCode) return;
      // 终态以后以数据库 run detail 为最终状态来源，SSE 只负责实时通知。
      getAgentRun(kbCode, runCode)
        .then((run) => {
          setCurrentRun(run);
          setRunCodeInput(run.runCode);
        })
        .catch((error) => {
          message.error(error instanceof Error ? error.message : "刷新 Agent run 详情失败");
        });
    },
    [kbCode, message]
  );

  const {
    events: runEvents,
    connectionStatus,
    error: eventConnectionError
  } = useAgentRunEvents({
    kbCode,
    runCode: currentRun?.runCode,
    enabled: !!currentRun?.runCode,
    onTerminal: (event) => refreshRunDetail(event.runCode)
  });

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

  const runViewModel = useMemo(
    () =>
      buildAgentRunViewModel({
        run: currentRun,
        events: runEvents,
        connectionStatus,
        connectionError: eventConnectionError
      }),
    [currentRun, runEvents, connectionStatus, eventConnectionError]
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
            goal: financeRetrievalGoal,
            question: financeRetrievalQuestion,
            runMode: "INTELLIGENT_TOOL_AGENT",
            createdBy: "frontend"
          }}
          onFinish={submit}
        >
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="智能模式使用 LLM planner + Java MCP tools"
            description="LLM 只生成 AgentDecision JSON；工具调用仍由后端校验、MCP tools/list 与 tools/call 执行。当前可直接调用的只读工具包括 readiness、文档/索引扫描、retrieve probe 和检索配置检查。"
          />
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
                    { label: "智能 Tool-use Agent", value: "INTELLIGENT_TOOL_AGENT" },
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
                  <Button
                    type="primary"
                    ghost
                    onClick={() =>
                      form.setFieldsValue({
                        goal: financeRetrievalGoal,
                        question: financeRetrievalQuestion,
                        runMode: "INTELLIGENT_TOOL_AGENT"
                      })
                    }
                  >
                    财务检索诊断
                  </Button>
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
            <AgentChainOverview run={currentRun} viewModel={runViewModel} />
            <Descriptions column={{ xs: 1, md: 2, xl: 3 }} bordered size="small">
              <Descriptions.Item label="runCode">{currentRun.runCode}</Descriptions.Item>
              <Descriptions.Item label="知识库">{currentRun.knowledgeBaseCode}</Descriptions.Item>
              <Descriptions.Item label="状态">{renderRunStatus(currentRun.status)}</Descriptions.Item>
              <Descriptions.Item label="模式">{currentRun.runMode}</Descriptions.Item>
              <Descriptions.Item label="创建人">{currentRun.createdBy}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(currentRun.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="完成时间">{formatDateTime(currentRun.finishedAt)}</Descriptions.Item>
              <Descriptions.Item label="Planner 决策">{runViewModel.decisionTrace.length}</Descriptions.Item>
              <Descriptions.Item label="工具调用">{runViewModel.tools.length}</Descriptions.Item>
              <Descriptions.Item label="待确认动作">{runViewModel.recommendedActions.length}</Descriptions.Item>
              <Descriptions.Item label="目标" span={2}>{currentRun.goal}</Descriptions.Item>
              <Descriptions.Item label="问题" span={2}>{currentRun.question || "-"}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="主回答">
            <AgentAnswerPanel answer={runViewModel.answer} />
          </Card>

          <Card title="执行进度">
            <AgentProgressPanel progress={runViewModel.progress} />
          </Card>

          <Card title="Agent 决策过程">
            <AgentDecisionTracePanel decisions={runViewModel.decisionTrace} />
          </Card>

          <Card title="推荐动作">
            <Space direction="vertical" size="middle" style={{ width: "100%" }}>
              {confirmMutation.error ? <ApiErrorAlert error={confirmMutation.error} /> : null}
              {rejectMutation.error ? <ApiErrorAlert error={rejectMutation.error} /> : null}
              <AgentActionCards
                actions={runViewModel.recommendedActions}
                confirmingActionCode={confirmMutation.variables?.actionCode}
                rejectingActionCode={rejectMutation.variables?.action.actionCode}
                onConfirm={(action) => confirmMutation.mutate(action)}
                onReject={(action, reason) => rejectMutation.mutate({ action, reason })}
              />
            </Space>
          </Card>

          <Card title="工具调用">
            <AgentToolCallsPanel tools={runViewModel.tools} />
          </Card>

          <Card title="证据摘要">
            <AgentEvidencePanel evidence={runViewModel.evidence} />
          </Card>

          <AgentDebugPanel viewModel={runViewModel} run={currentRun} connectionError={eventConnectionError} />
        </>
      ) : null}
    </Space>
  );
}

function renderRunStatus(status: string) {
  const color = status === "SUCCEEDED" ? "green" : status === "FAILED" ? "red" : status === "WAITING_CONFIRMATION" ? "gold" : "blue";
  return <Tag color={color}>{status}</Tag>;
}

function AgentChainOverview({ run, viewModel }: { run: AgentRun; viewModel: AgentRunViewModel }) {
  const toolNames = viewModel.tools.map((tool) => tool.toolName).filter((toolName, index, all) => all.indexOf(toolName) === index);
  const hasFinalAnswer = viewModel.decisionTrace.some((decision) => decision.action === "FINAL_ANSWER");
  return (
    <Card size="small" style={{ marginBottom: 16 }} styles={{ body: { padding: 12 } }}>
      <Space direction="vertical" size="small" style={{ width: "100%" }}>
        <Space wrap>
          <Tag color={viewModel.decisionTrace.length > 0 ? "blue" : "default"}>Planner 决策 {viewModel.decisionTrace.length}</Tag>
          <Tag color={viewModel.tools.length > 0 ? "geekblue" : "default"}>MCP 工具调用 {viewModel.tools.length}</Tag>
          <Tag color={viewModel.recommendedActions.length > 0 ? "gold" : "default"}>推荐动作 {viewModel.recommendedActions.length}</Tag>
          {viewModel.planner.calls.length > 0 ? <Tag color="cyan">Planner 总耗时 {viewModel.planner.totalDurationMs} ms</Tag> : null}
          <Tag color={hasFinalAnswer ? "green" : run.status === "WAITING_CONFIRMATION" ? "gold" : "default"}>
            {hasFinalAnswer ? "已生成最终回答" : run.status === "WAITING_CONFIRMATION" ? "等待人工确认" : "未完成最终回答"}
          </Tag>
        </Space>
        <Typography.Text type="secondary">
          {toolNames.length > 0 ? `已调用工具：${toolNames.join("、")}` : "尚未记录 MCP 工具调用。"}
        </Typography.Text>
      </Space>
    </Card>
  );
}
