# RAG Agent 改造计划

## 1. 当前定位

在现有 RAG 系统上方新增一个可审计、可确认、可演示的运维诊断 Agent。

项目同时保留两条 Agent 路线：

1. **Legacy 固定流程 Agent**：确定性业务工作流，用于 readiness、索引任务、检索探测等固定诊断路径。
2. **Intelligent Tool-use Agent**：单 Agent、LLM 决策、LangGraph 循环编排，根据工具注册表动态选择 Java / MCP / CLI 工具。

核心边界：

1. Java 是业务权威、run 状态中心和写操作执行方。
2. Python 是 LangGraph Runtime，只负责工具编排、观察结果整理和推荐动作生成。
3. 前端负责创建 run、展示 timeline、展示 recommended actions、触发 confirm/reject。
4. Python 不生成 `runCode / stepCode / actionCode`，这些由 Java 统一生成并落库。
5. 写操作、`MEDIUM/HIGH` 风险工具、`requiresConfirmation=true` 工具必须进入 human-in-the-loop。

## 2. 系统分工

### 2.1 Java 后端

职责：

1. 提供 Agent Run API。
2. 持久化 `agent_run / agent_step / agent_action`。
3. 暴露受控 Java 工具和 Tool Registry definitions。
4. 调用 Python Agent Runtime。
5. 将 Python 返回的 `steps / recommendedActions` 落库。
6. 根据 Runtime 返回的 action 决定 runStatus。
7. confirm/reject 后执行或拒绝写操作。

runStatus 规则：

1. `SUCCEEDED`：Runtime 成功，且没有待确认动作。
2. `WAITING_CONFIRMATION`：Python 返回 `recommendedActions`，Java 落库为 `PENDING_CONFIRMATION`。
3. `FAILED`：Runtime 失败、工具失败不可恢复、协议校验失败或确认执行失败。

### 2.2 Python AI Service

职责：

1. 承载 LangGraph Runtime。
2. 保留 legacy graph：`build_readiness_diagnosis_graph()`。
3. 新增 intelligent graph：`build_intelligent_tool_agent_graph()`。
4. 从 Tool Registry 读取工具定义。
5. 执行只读工具，写回 observations。
6. 强制拦截高风险或写工具，转成 recommended action。
7. 每次 LLM 决策落 `LLM_DECISION` step，每次工具调用落 `TOOL_CALL` step。

Python 不做：

1. 不直接写业务库。
2. 不直接执行 retry/rebuild 等写操作。
3. 不让 LLM 直接拼 shell 字符串。
4. 不把 chain-of-thought 入库。

### 2.3 前端

职责：

1. Agent 工作台创建 run。
2. 支持 `DIAGNOSE_ONLY / DIAGNOSE_AND_RECOMMEND / INTELLIGENT_TOOL_AGENT`。
3. 展示 summary、runStatus、timeline。
4. 展示 action 风险、payload、confirm/reject。
5. 展示工具 observation 的 JSON 输出。

## 3. 数据模型和接口

### 3.1 Java API

```text
POST /api/knowledge-bases/{kbCode}/agent/runs
GET  /api/knowledge-bases/{kbCode}/agent/runs/{runCode}
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject
GET  /api/internal/agent/tools
POST /api/internal/agent/tools/{toolName}/execute
```

创建 run 请求：

```json
{
  "goal": "诊断这个知识库为什么不能问答",
  "question": "可选问题",
  "runMode": "INTELLIGENT_TOOL_AGENT",
  "createdBy": "frontend"
}
```

### 3.2 Python Runtime API

```text
POST /v1/agent/runs
```

请求由 Java 发起：

```json
{
  "runCode": "AR-xxx",
  "kbCode": "day20-cn-kb",
  "goal": "诊断这个知识库为什么不能问答",
  "question": "可选问题",
  "runMode": "INTELLIGENT_TOOL_AGENT"
}
```

Python 返回草案，不返回 Java 主键：

```json
{
  "status": "SUCCEEDED",
  "summary": "Agent 已生成待确认动作：embedding.rebuild.submit",
  "steps": [],
  "recommendedActions": []
}
```

### 3.3 持久化表

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
step_type: NODE / TOOL_CALL / LLM_DECISION
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

## 4. Legacy 固定流程 Agent

固定图：

```text
parse_goal
  -> system_health_check
  -> kb_readiness_check
  -> documents_status_scan
  -> indexing_tasks_scan
  -> qa_retrieve_probe
  -> diagnose
  -> recommend_actions
  -> generate_report
```

目标：

1. 提供确定性诊断链路。
2. 覆盖 readiness、索引失败、检索质量三个业务场景。
3. 作为 debug / fallback / 面试中“确定性业务工作流 Agent”案例。

## 5. Intelligent Tool-use Agent

智能图：

```text
load_tools
  -> llm_plan
  -> route_decision
      -> execute_readonly_tool -> llm_plan
      -> create_recommended_action -> END
      -> final_report -> END
      -> fail_report -> END
```

状态结构：

1. `tools`
2. `messages`
3. `decision`
4. `observations`
5. `tool_call_count`
6. `steps`
7. `recommended_actions`
8. `summary`
9. `error_message`

关键行为：

1. `llm_plan` 只接受严格 JSON 决策。
2. 每轮决策做 JSON parse、枚举校验、toolName 白名单校验、arguments schema 校验。
3. 非法 JSON 重试一次；仍失败则生成 failed `LLM_DECISION` step。
4. 工具调用上限默认 6 次；超过后 run failed。
5. 工具原始输出写 step output JSON。
6. 回灌 LLM 的 observation 先裁剪/摘要，避免把大 JSON 原样塞回。

## 6. Tool Registry v2

ToolDefinition 字段：

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

工具来源：

1. `JAVA`：Java 内部受控工具。
2. `MCP`：先做 fake MCP MVP，验证 discovery/definition/call/observation 链路。
3. `CLI`：只读、白名单、schema 化、模板化命令适配器。

CLI 边界：

1. CLI Adapter 不是通用 shell 代理。
2. LLM 不允许传入任意 `command`。
3. 当前仅开放 `cli.git.status`，固定执行 `git status --short`。
4. 写 CLI 未来必须走 Java confirm/action 流程。

## 7. AgentDecision 协议

`CALL_TOOL` 示例：

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

`REQUEST_CONFIRMATION` 示例：

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

`FINAL_ANSWER` 示例：

```json
{
  "action": "FINAL_ANSWER",
  "toolName": null,
  "arguments": {},
  "reason": "已有工具观察结果足够生成结论。",
  "finalAnswer": "当前未发现阻断问答的 readiness 问题。",
  "riskLevel": null
}
```

安全要求：

1. `REQUEST_CONFIRMATION` 不能只依赖 LLM 自觉输出。
2. Runtime 必须根据 Tool Registry 强制拦截。
3. 即使 LLM 输出 `CALL_TOOL`，只要目标工具是 `WRITE`、`MEDIUM/HIGH` 或 `requiresConfirmation=true`，也必须转成 `recommendedActions`。
4. Java 统一落库为 `PENDING_CONFIRMATION`。

## 8. Week 1-3 任务拆分

### Week 1：Agent 主链路

| Day | 目标 | 状态 |
| --- | --- | --- |
| Day 1 | 状态模型、工具协议、三张表 | Done |
| Day 2 | Agent 查询 API 与 Service 骨架 | Done |
| Day 3 | P0 只读工具封装 | Done |
| Day 4 | Python LangGraph 最小图 | Done |
| Day 5 | Java 调 Python Runtime 并落库 | Done |
| Day 6 | documents/status 与 indexing/tasks 扫描 | Done |
| Day 7 | readiness / failed task 端到端验收 | Done |

### Week 2：运维闭环和前端展示

| Day | 目标 | 状态 |
| --- | --- | --- |
| Day 8 | `document.indexing_task.retry` 确认执行 | Done |
| Day 9 | `embedding.rebuild.submit` 确认执行 | Done |
| Day 10 | 前端 Agent 工作台基础 | Done |
| Day 11 | timeline 和 action 卡片 | Done |
| Day 12 | confirm/reject 前端闭环 | Done |
| Day 13 | `qa.retrieve.probe` Dense / Hybrid 探测 | Done |
| Day 14 | README、接口说明、面试材料 | Done |

### Week 3：单 Agent 智能 Tool-use 改造

| Day | 目标 | 状态 |
| --- | --- | --- |
| Day 15 | 文档和状态模型收口，新增智能 runMode / AgentState / AgentDecision / ToolDefinition v2 | Done |
| Day 16 | Tool Registry v2，Java definitions API，Python 拉取 Java tool definitions | Done |
| Day 17 | 智能 LangGraph 主循环，`LLM_DECISION -> TOOL_CALL -> observation -> next decision` | Done |
| Day 18 | JSON 决策校验、失败恢复、fake/mock LLM 测试 | Done |
| Day 19 | Runtime 安全拦截，recommended action 和 Java `WAITING_CONFIRMATION` | Done |
| Day 20 | fake MCP + 只读 CLI MVP，证明新增工具不改主循环 | Done |
| Day 21 | 端到端验收、确定性演示数据、面试展示材料 | In Progress |

## 9. 固定演示场景

演示 1：readiness 异常

准备数据：

```text
day20-cn-kb readiness 返回 reembedRequired=true
```

期望 timeline：

```text
LLM_DECISION -> kb.readiness.check -> observation -> LLM_DECISION -> recommended embedding.rebuild.submit -> WAITING_CONFIRMATION
```

演示 2：索引任务失败

准备数据：

```text
indexing.tasks.scan 返回至少一条 FAILED task
```

期望 timeline：

```text
LLM_DECISION -> indexing.tasks.scan -> observation -> LLM_DECISION -> recommended document.indexing_task.retry -> WAITING_CONFIRMATION
```

演示 3：MCP + CLI 只读工具

输入：

```text
检查当前项目状态，并结合 git 状态给出诊断
```

期望 timeline：

```text
LLM_DECISION -> mcp.repo.status.inspect -> observation -> LLM_DECISION -> cli.git.status -> observation -> LLM_DECISION -> FINAL_ANSWER -> SUCCEEDED
```

## 10. 测试计划

后端：

1. 创建 run 后能正确调用 Python Runtime。
2. Runtime steps/actions 由 Java 生成 `stepCode/actionCode` 并落库。
3. 有 recommended action 时 run 进入 `WAITING_CONFIRMATION`。
4. confirm/reject 状态流转正确。
5. 未确认时写操作不会执行。
6. `HIGH` 风险 action 禁止执行。

Python：

1. Legacy graph 节点顺序稳定。
2. Intelligent graph 可循环调用工具。
3. fake/mock LLM 覆盖非法 JSON、未知工具、schema mismatch、最大工具次数。
4. Runtime 强制拦截写工具和中高风险工具。
5. fake MCP 和只读 CLI 可通过 Tool Registry 接入。

前端：

1. 可创建三种 runMode。
2. 可展示 `LLM_DECISION / TOOL_CALL / NODE` timeline。
3. 可展示 action 卡片和 confirm/reject。
4. API 错误展示 message 和 requestId。
5. `npm run build` 通过。

联调：

1. Python `/health` 正常。
2. Java `/api/health` 可达，并记录 PostgreSQL/Redis/AI Gateway/embedding/llm 的实际能力状态。
3. Frontend dev server 正常。
4. 通过前后端 API 创建 `INTELLIGENT_TOOL_AGENT` run。
5. 验证 Java tool、fake MCP tool、只读 CLI tool 可在同一主循环中工作。

## 11. 面试表达

简历 bullet：

> 在企业知识库 RAG 系统基础上引入 LangGraph，构建 RAG 运维诊断 Agent，将系统健康检查、问答 readiness、文档状态扫描、索引任务扫描、Dense / Hybrid 检索探测、失败重试和重嵌入封装为受控工具。进一步演进为单 Agent Tool-use 模式，通过 Tool Registry 统一接入 Java / MCP / CLI 工具，由 Runtime 强制拦截写操作和中高风险工具，并通过 agent_run / agent_step / agent_action 记录执行轨迹与 human-in-the-loop 审计结果。

面试解释：

> 我没有把 Agent 做成纯聊天入口，而是围绕 RAG 系统真实运维问题设计了一个受控 Tool-use Agent。LangGraph 负责循环编排：LLM 输出严格 JSON 决策，Runtime 校验后调用工具，把 observation 裁剪后回灌，再进入下一轮决策。Java 仍然是业务权威和安全边界，涉及重试索引、重嵌入等写操作时必须生成 recommendedAction，由前端确认后 Java 执行。这样既能展示固定业务工作流 Agent，也能展示 LLM 驱动的智能 Tool-use Agent。
