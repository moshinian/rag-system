# Day 14：Agent 文档、架构图与面试材料收口计划

## 目标

对齐 `plan.md` 的 Day 14：更新 README、架构图、接口说明、简历 bullet 和面试讲稿，把 Day 1-13 已经完成的 LangGraph RAG 运维诊断 Agent 收口成可恢复、可演示、可面试讲解的项目资产。

Day 14 不继续新增主功能，重点是把真实实现讲清楚，并避免把尚未完成的能力写成已完成。

## 当前输入

当前已完成能力：

- Java 是 Agent Run 状态中心和业务权威。
- Python `rag-ai-service` 是 LangGraph Agent Runtime。
- Java 统一生成并持久化：
  - `runCode`
  - `stepCode`
  - `actionCode`
- Python Runtime 不生成业务 code，不写业务 DB。
- 已完成 Agent 三张表：
  - `agent_run`
  - `agent_step`
  - `agent_action`
- 已完成只读工具：
  - `system.health.check`
  - `kb.readiness.check`
  - `documents.status.scan`
  - `indexing.tasks.scan`
  - `qa.retrieve.probe`
- 已完成待确认写 action：
  - `document.indexing_task.retry`
  - `embedding.rebuild.submit`
- 前端 Agent 工作台已支持：
  - 创建 run
  - 查询 run
  - 展示 summary / timeline / action cards
  - confirm / reject
  - 展示执行结果
- 两个优先演示场景已完成闭环：
  - `reembedRequired -> embedding.rebuild.submit -> human confirm -> Java 执行`
  - `FAILED indexing task -> document.indexing_task.retry -> human confirm -> Java 执行`
- 可选检索诊断场景已完成简化版：
  - `qa.retrieve.probe` 对比 Dense / Hybrid。

## 边界约束

- 不把“Python Runtime 调用 Java 真实工具 HTTP API”写成已完成；当前仍有静态工具客户端阶段。
- 不把 MCP、多 Agent、多轮聊天写成已完成。
- 不把 `qa.ask.probe` 写成已完成。
- 不把前端 Dense / Hybrid 专门对比组件写成已完成；当前 `qa.retrieve.probe` 结果复用 step `outputJson` 展示。
- 不把危险操作自动执行写成能力；写操作必须 human-in-the-loop。
- 不夸大 LLM 权限；LLM/Agent 只能诊断和推荐，不能绕过 Java 白名单。
- 根 README 如果更新，只写当前真实完成能力和明确后续方向。

## 产出物范围

### 1. Agent README 收口

目标文件：

- `docs/work/rag-agent/README.md`

计划补充：

- Agent 定位：RAG 运维诊断 Agent，不是普通聊天机器人。
- 三层职责：
  - Java：业务权威、工具白名单、状态中心、写操作确认执行。
  - Python：LangGraph Runtime、状态图、工具观察、诊断 summary/action draft。
  - Frontend：run 工作台、timeline、action card、human-in-the-loop。
- 当前能力清单。
- 当前未完成清单。
- Demo 场景入口。
- 下次恢复入口。

### 2. 架构图

目标文件建议：

- `docs/work/rag-agent/architecture.md`

建议用 Mermaid，不引入图片资产。

至少包含三张图：

1. 总体分层图：
   - Frontend
   - Java Backend
   - Python Agent Runtime
   - Business DB / Vector / Redis / Model Provider
2. Agent Run 时序图：
   - 前端创建 run
   - Java 创建 runCode
   - Java 调 Python Runtime
   - Python 运行 LangGraph
   - Java 落库 steps/actions
   - 前端查询展示
3. Human-in-the-loop 写操作图：
   - action `PENDING_CONFIRMATION`
   - 用户 confirm/reject
   - Java 校验归属、状态、风险、白名单
   - Java 执行业务写操作
   - Java 更新 action/run

### 3. 接口说明

目标文件建议：

- `docs/work/rag-agent/api.md`

内容：

- Java Agent API：
  - `POST /api/knowledge-bases/{kbCode}/agent/runs`
  - `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}`
  - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm`
  - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject`
- Python Agent Runtime API：
  - `POST /v1/agent/runs`
- 工具白名单：
  - 只读工具
  - 待确认写 action
- 状态模型：
  - `AgentRunStatus`
  - `AgentStepStatus`
  - `AgentActionStatus`
- 请求/响应示例。
- requestId / error 展示口径。

### 4. Demo Runbook

目标文件建议：

- `docs/work/rag-agent/demo-runbook.md`

内容：

- 场景 1：readiness 异常触发重嵌入推荐。
- 场景 2：FAILED indexing task 触发 retry 推荐。
- 场景 3：Dense / Hybrid 检索探测。
- 每个场景写清：
  - 输入目标 / question。
  - 预期关键 step。
  - 预期 action。
  - 前端观察点。
  - 后端验证点。
  - 不应发生的事，例如未确认前不能执行写操作。

### 5. 简历 bullet

目标文件建议：

- `docs/work/rag-agent/resume-and-interview.md`

简历 bullet 要保守、可被代码支撑：

```text
在企业知识库 RAG 系统基础上引入 LangGraph，设计并实现 RAG 运维诊断 Agent，将系统健康检查、问答 readiness、文档状态、索引任务、Dense/Hybrid 检索探测封装为受控工具；Java 后端负责 Agent Run 状态中心、工具白名单、审计落库和 human-in-the-loop 写操作确认，Python 仅承担 LangGraph Runtime 编排，前端展示 timeline、推荐动作和确认执行结果。
```

可拆分为 2-3 条：

- Agent 编排与状态审计。
- Java 安全边界与 human-in-the-loop。
- RAG 运维诊断工具和可解释展示。

### 6. 面试讲稿

目标文件建议：

- `docs/work/rag-agent/resume-and-interview.md`

建议包含：

- 30 秒版本。
- 2 分钟版本。
- 深挖问答：
  - 为什么不用纯聊天 Agent？
  - 为什么 Java 是状态中心？
  - 为什么 Python 不生成 code？
  - 为什么写操作要 human-in-the-loop？
  - LangGraph 在这里解决了什么？
  - 两个演示场景怎么讲？
  - `qa.retrieve.probe` 的价值是什么？
  - 当前还有什么没做？

## 建议执行顺序

1. 新增 `architecture.md`，先把三层架构和时序图画清楚。
2. 新增 `api.md`，整理接口、状态、工具白名单。
3. 新增 `demo-runbook.md`，把三个演示场景写成可操作脚本。
4. 新增 `resume-and-interview.md`，整理简历 bullet 和讲稿。
5. 回头更新 `docs/work/rag-agent/README.md`，把这些文档挂到入口。
6. 视情况更新根 `README.md` 的当前能力摘要，但只写已经完成的能力。
7. 更新 `current-status.md`，标记 Day 14 完成。

## 验收标准

### 文档验收

- `docs/work/rag-agent/README.md` 能作为恢复入口。
- `architecture.md` 能讲清 Java / Python / Frontend 分工。
- `api.md` 能讲清 API、状态模型、工具白名单和确认执行边界。
- `demo-runbook.md` 能支持三类演示。
- `resume-and-interview.md` 能直接用于简历和面试准备。
- 所有文档都只描述当前真实完成能力。

### 设计口径验收

- 明确 Java 是业务权威和 Agent Run 状态中心。
- 明确 Python 只做 LangGraph Agent Runtime。
- 明确 Python 不生成 `runCode / stepCode / actionCode`。
- 明确 Python 不直接执行写操作。
- 明确写操作必须经 Java confirm/reject human-in-the-loop。
- 明确 `qa.retrieve.probe` 当前是只读检索诊断。

### 工程验收

- Day 14 如果只改 Markdown，可以不跑全量测试。
- 至少执行一次 Markdown 链接/路径人工检查。
- 如果更新根 README 或代码引用片段，确认没有把计划项写成已完成项。
- 完成后更新 `docs/work/rag-agent/current-status.md`。

## Day 14 后状态

Day 14 完成后，`rag-agent` 两周计划进入收口状态。

后续如果继续开发，建议从两个方向二选一：

1. 工程闭环增强：
   - Python Runtime 调用 Java 真实工具 HTTP API。
   - 前端新增 Dense / Hybrid 专门对比组件。
2. 面试资产增强：
   - 补演示截图。
   - 补录屏脚本。
   - 补高频追问回答。
