# 基于 LangGraph 的 RAG 运维诊断 Agent 改造计划

## Summary

在现有 RAG 系统基础上新增“RAG 运维诊断 Agent”，用 LangGraph 承接 Agent 状态图和工具编排，用 Java 后端承接受控工具、安全边界、落库审计和确认执行，用前端展示执行轨迹与 human-in-the-loop 操作。

核心原则：

1. Java 是 Agent Run 的权威状态中心。
2. Python 是 LangGraph Agent Runtime，只负责一次诊断执行的计算与建议生成。
3. Python 不生成 `runCode / stepCode / actionCode`，这些由 Java 统一生成和落库。
4. `WAITING_CONFIRMATION` 是 Java 的 run 状态，不要求 LangGraph 在确认后继续执行。
5. v1 优先打通两个演示场景：`reembedRequired` 和 `FAILED indexing task`。

## Key Changes

### 架构分工

`rag-backend`：

1. 新增 Agent API：创建 run、查询 run、确认/拒绝 action。
2. 新增 `agent_run / agent_step / agent_action` 三张表。
3. 封装 Agent 可调用的受控工具。
4. 调用 `rag-ai-service` 的 LangGraph Agent Runtime。
5. 根据 Python 返回的步骤和推荐动作生成 `stepCode / actionCode` 并落库。
6. 对写操作执行白名单、风险等级和确认校验。

`rag-ai-service`：

1. 新增 `app/agent/` 模块。
2. 引入 LangGraph。
3. 定义 `AgentState` 和固定状态图。
4. 调用 Java 提供的只读工具接口。
5. 返回诊断步骤、诊断结论和推荐动作草案。
6. 不直接写业务库，不直接执行重试或重嵌入。

`rag-frontend`：

1. 新增 Agent 工作台。
2. 支持创建诊断 run。
3. 展示 step timeline。
4. 展示 recommended actions。
5. 支持确认或拒绝 action。
6. 展示执行结果、错误信息和 requestId。

### LangGraph v1 状态图

```text
START
  ↓
parse_goal
  ↓
system_health_check
  ↓
kb_readiness_check
  ↓
documents_status_scan
  ↓
indexing_tasks_scan
  ↓
should_run_retrieve_probe?
      ├── yes → qa_retrieve_probe
      └── no
  ↓
diagnose
  ↓
recommend_actions
  ↓
generate_report
  ↓
END
```

LangGraph 只负责诊断和推荐。Java 根据返回结果判断：

1. 如果存在 `requiresConfirmation=true` 的 action，`agent_run.status = WAITING_CONFIRMATION`。
2. 如果没有待确认 action，`agent_run.status = SUCCEEDED`。
3. 如果 Agent Runtime 或工具调用失败，`agent_run.status = FAILED` 并记录 `error_message`。

### MVP 工具范围

P0 必做只读工具：

1. `system.health.check`
2. `kb.readiness.check`

P1 强烈建议完成：

1. `documents.status.scan`
2. `indexing.tasks.scan`
3. `document.indexing_task.retry`

P2 有时间完成：

1. `qa.retrieve.probe`
2. `embedding.rebuild.submit`

`qa.retrieve.probe` 设计为 Dense / Hybrid 对比探测：

1. 同一问题分别执行 `DENSE` 和 `HYBRID`。
2. 返回命中数、keyword 命中数、TopK sources 和耗时。
3. 标记 keyword 零命中、Hybrid 无增益、检索结果为空等诊断信号。

暂缓：

1. `qa.ask.probe`
2. `kb.enable.with_failed_task_retry`
3. `redis.probe.run`
4. 多轮聊天
5. MCP
6. 多 Agent 协作
7. 自动危险操作

## Public Interfaces And Data Model

### Java API

```text
POST /api/knowledge-bases/{kbCode}/agent/runs
GET  /api/knowledge-bases/{kbCode}/agent/runs/{runCode}
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject
```

`POST /agent/runs` 请求：

```json
{
  "goal": "诊断这个知识库为什么不能问答",
  "question": "可选，用于检索探测",
  "runMode": "DIAGNOSE_AND_RECOMMEND"
}
```

`runMode` 支持：

```text
DIAGNOSE_ONLY
DIAGNOSE_AND_RECOMMEND
```

### Python Agent Runtime API

```text
POST /v1/agent/runs
```

请求由 Java 发起，包含：

```json
{
  "runCode": "AR-xxx",
  "kbCode": "day20-cn-kb",
  "goal": "诊断这个知识库为什么不能问答",
  "question": "可选检索探测问题",
  "runMode": "DIAGNOSE_AND_RECOMMEND"
}
```

Python 返回步骤和动作草案，不返回最终主键：

```json
{
  "status": "SUCCEEDED",
  "summary": "知识库不可问答，主要原因是 embedding 配置变化后尚未重嵌入。",
  "steps": [],
  "recommendedActions": []
}
```

### 数据表

`agent_run`：

```text
run_code
knowledge_base_id
goal
question
run_mode
status: RUNNING / WAITING_CONFIRMATION / SUCCEEDED / FAILED
summary
error_message
created_by
created_at
updated_at
finished_at
```

`agent_step`：

```text
run_code
step_code
node_name
tool_name
step_type: NODE / TOOL_CALL / REASONING
status: PENDING / RUNNING / SUCCEEDED / FAILED / SKIPPED
input_json
output_json
duration_ms
error_message
started_at
finished_at
created_at
updated_at
```

`agent_action`：

```text
action_code
run_code
tool_name
title
reason
risk_level: LOW / MEDIUM / HIGH
requires_confirmation
status: PENDING_CONFIRMATION / CONFIRMED / EXECUTING / SUCCEEDED / FAILED / REJECTED
action_payload
confirmed_by
confirmed_at
executed_at
result_json
error_message
created_at
updated_at
```

风险策略：

1. `LOW`：允许确认执行。
2. `MEDIUM`：必须确认执行。
3. `HIGH`：v1 禁止执行，只能展示建议。

## Implementation Plan

第 1 周：Agent 主线跑通

1. Day 1：确定 `AgentState`、工具协议、状态枚举和三张表。
2. Day 2：Java 实现 `agent_run / agent_step / agent_action` 持久化与查询 API。
3. Day 3：Java 封装 `system.health.check` 和 `kb.readiness.check` 工具。
4. Day 4：Python 引入 LangGraph，实现 `parse_goal -> health -> readiness -> diagnose -> recommend -> report` 最小图。
5. Day 5：Java 调用 Python Agent Runtime，并将返回 steps/actions 落库。
6. Day 6：实现 `documents.status.scan` 和 `indexing.tasks.scan`。
7. Day 7：跑通第一个完整诊断场景：`readiness 异常 -> 推荐重嵌入 -> WAITING_CONFIRMATION`。

第 2 周：运维闭环和展示

1. Day 8：实现 `document.indexing_task.retry` 确认执行。
2. Day 9：实现 `embedding.rebuild.submit` 确认执行。
3. Day 10：前端新增 Agent 工作台：创建 run、查询 run、展示 summary。
4. Day 11：前端展示 timeline 和推荐动作卡片。
5. Day 12：前端接入 confirm/reject，并展示执行结果。
6. Day 13：补 `qa.retrieve.probe` 简化版 Dense / Hybrid 对比；若时间不足，降级为 P2。
7. Day 14：更新 README、架构图、接口说明、简历 bullet 和面试讲稿。

第 3 周：单 Agent 智能 Tool-use 改造

目标是把当前固定流程式 Agent 演进为“单 Agent、LLM 驱动、可循环调用工具”的智能 Tool-use Agent。旧固定图保留为 legacy/debug：`build_readiness_diagnosis_graph()`；新增智能图：`build_intelligent_tool_agent_graph()`。

智能 Agent run 状态规则：

1. `SUCCEEDED`：LLM 生成 `FINAL_ANSWER`，且没有待确认动作。
2. `WAITING_CONFIRMATION`：Python Runtime 返回 `recommendedActions`，由 Java 统一落库为 `PENDING_CONFIRMATION`。
3. `FAILED`：LLM 决策解析失败、schema 校验失败、工具执行失败不可恢复，或超过最大工具调用次数。

LangGraph 智能图：

```text
load_tools
  -> llm_plan
  -> route_decision
      -> execute_readonly_tool -> llm_plan
      -> create_recommended_action -> END
      -> final_report -> END
      -> fail_report -> END
```

`execute_readonly_tool` 执行工具、写入 observation、落 `TOOL_CALL` step，然后回到 `llm_plan`。不单独保留 `observe` 节点。

AgentState 显式保存：

1. `tools`
2. `messages`
3. `decision`
4. `observations`
5. `tool_call_count`
6. `steps`
7. `recommended_actions`
8. `summary`
9. `error_message`

Tool Definition v2 字段：

1. `schemaVersion`
2. `name`
3. `description`
4. `inputSchema`
5. `outputSchema`
6. `riskLevel`
7. `executionMode`
8. `sourceType`
9. `requiresConfirmation`
10. `timeoutMs`

AgentDecision 严格 JSON 示例：

```json
{
  "action": "CALL_TOOL",
  "toolName": "kb.readiness.check",
  "arguments": {
    "kbCode": "day20-cn-kb"
  },
  "reason": "需要先检查知识库是否具备问答条件。",
  "finalAnswer": null,
  "riskLevel": "LOW"
}
```

```json
{
  "action": "REQUEST_CONFIRMATION",
  "toolName": "embedding.rebuild.submit",
  "arguments": {
    "kbCode": "day20-cn-kb"
  },
  "reason": "readiness 显示 reembedRequired=true，需要人工确认后提交重嵌入任务。",
  "finalAnswer": null,
  "riskLevel": "MEDIUM"
}
```

```json
{
  "action": "FINAL_ANSWER",
  "toolName": null,
  "arguments": {},
  "reason": "已有工具观察结果足够生成结论。",
  "finalAnswer": "当前未发现阻断问答的 readiness 问题，系统健康和知识库 readiness 均正常。",
  "riskLevel": null
}
```

关键安全边界：

1. `REQUEST_CONFIRMATION` 不能只依赖 LLM 自觉。
2. 即使 LLM 输出 `CALL_TOOL`，只要目标工具是 `WRITE`、`MEDIUM/HIGH` 风险，或 `requiresConfirmation=true`，Runtime 都必须转成 Python `recommendedActions`。
3. Java 统一生成 `actionCode` 并落库为 `PENDING_CONFIRMATION`。
4. 前端 confirm/reject 后再由 Java 执行。
5. 工具原始结果写入 step output JSON；回灌给 LLM 的 observation 必须先裁剪或摘要。

第 3 周任务拆分：

1. Day 15：文档和状态模型收口，新增智能 runMode、AgentState、AgentDecision、AgentObservation、ToolDefinition v2。
2. Day 16：Tool Registry v2，Java 暴露内部 tool definitions 查询接口，Python 拉取 Java tool definitions。
3. Day 17：智能 LangGraph 主循环，新增 `build_intelligent_tool_agent_graph()`，每次 LLM 决策落 `LLM_DECISION` step，每次工具调用落 `TOOL_CALL` step。
4. Day 18：LLM 决策校验和失败恢复，非法 JSON 重试一次，工具循环次数默认上限为 6，测试使用 fake LLM / mock LLM。
5. Day 19：安全拦截和 recommended action，根据 Tool Registry 强制识别 `WRITE / MEDIUM/HIGH / requiresConfirmation`，有 recommended action 时 Java 将 run 标记为 `WAITING_CONFIRMATION`。
6. Day 20：MCP/CLI 最小接入，只接一个 fake MCP tool 和一个只读 CLI tool；CLI 不是 shell agent，不允许 LLM 传任意 command。
7. Day 21：端到端验收和面试材料，验收 Java tool + fake MCP tool + 只读 CLI tool 在同一主循环中工作。

## Demo Scenarios

优先场景 1：readiness 异常

```text
输入：诊断 day20-cn-kb 为什么不能问答
输出：health 正常，readiness 显示 reembedRequired=true，推荐 embedding.rebuild.submit，等待用户确认
```

优先场景 2：索引任务失败

```text
输入：检查这个知识库有没有索引异常
输出：发现 FAILED indexing task，展示失败原因，推荐 document.indexing_task.retry，等待用户确认
```

可选场景 3：检索质量诊断

```text
输入：用某个问题检查 Dense / Hybrid 检索效果
输出：展示 DENSE 和 HYBRID 命中、keywordHitCount、TopK sources 和耗时差异
```

## Test Plan

后端测试：

1. 创建 Agent run 后状态为 `RUNNING` 并正确落库。
2. Python 返回 steps/actions 后，Java 生成 `stepCode / actionCode` 并持久化。
3. `reembedRequired=true` 时生成 `embedding.rebuild.submit` action。
4. 存在 `FAILED` 索引任务时生成 `document.indexing_task.retry` action。
5. 有待确认 action 时，run 状态变为 `WAITING_CONFIRMATION`。
6. 未确认时写操作不会执行。
7. 确认后只允许执行白名单工具。
8. `HIGH` 风险 action 不能执行。
9. reject 后 action 状态变为 `REJECTED`。
10. Agent Runtime 失败时记录 `error_message`。

Python 测试：

1. LangGraph 最小图按预期节点顺序执行。
2. readiness 异常能生成稳定诊断结论。
3. 工具调用失败能返回 step error。
4. `qa.retrieve.probe` 可按条件跳过。
5. recommended actions 只返回工具名和 payload 草案，不生成 actionCode。

前端验证：

1. 可以创建诊断 run。
2. 可以展示 summary、status 和 timeline。
3. 可以展示 action 卡片、风险等级和确认按钮。
4. confirm/reject 后页面状态刷新。
5. API 错误展示 message 和 requestId。
6. `npm run build` 通过。

## Resume Positioning

简历 bullet：

> 在企业知识库 RAG 系统基础上引入 LangGraph，构建 RAG 运维诊断 Agent，将系统健康检查、问答 readiness、文档状态扫描、索引任务扫描、Dense / Hybrid 检索探测、失败重试和重嵌入封装为受控工具。Agent 通过状态图完成任务解析、工具调用、结果观察、原因诊断和修复计划生成；Java 后端负责工具白名单、安全边界与确认执行，对重试索引、重嵌入等写操作引入 human-in-the-loop，并通过 agent_run / agent_step / agent_action 记录执行轨迹与审计结果。

面试解释：

> 我没有把 Agent 做成纯聊天入口，而是围绕 RAG 系统真实运维问题设计了一个受控 Agent 工作流。LangGraph 负责状态流转和工具编排，每个节点对应明确诊断动作，例如 health、readiness、文档状态、索引任务、检索探测。LLM 只辅助诊断总结和报告生成，不能绕过 Java 后端工具白名单。涉及重试索引、重嵌入等写操作时，必须进入 human-in-the-loop，由用户确认后执行，并且全过程落库可追踪。

## Assumptions

1. 本次 MVP 主打“RAG 运维诊断 Agent”。
2. 周期按 2 周安排。
3. Java 是业务权威和状态中心。
4. Python 是 LangGraph Agent Runtime。
5. 第一版不做确认后回到 LangGraph 继续执行。
6. 第一版优先完成 `reembedRequired` 和 `FAILED indexing task` 两个演示场景。
7. LLM 可以辅助总结，但不参与权限判断。
8. 不新增独立 Agent 服务，先在 `rag-ai-service` 内隔离 `app/agent/`。
