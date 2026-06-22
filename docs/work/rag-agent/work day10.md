# Day 10：前端 Agent 工作台最小闭环计划

## 目标

Day 10 的目标是把后端已经完成的 Agent 运行能力接到前端，形成第一个可操作的 Agent 工作台页面：

```text
选择知识库
  -> 进入 Agent 工作台
  -> 输入诊断目标
  -> 创建 Agent run
  -> 查询 Agent run
  -> 展示 summary / status
  -> 展示 steps / actions 基础信息
```

Day 10 只做“创建 run、查询 run、基础展示”。不做复杂 timeline，不做 action 卡片确认按钮，不做前端 confirm/reject。

## 当前输入

后端已完成：

1. `POST /api/knowledge-bases/{kbCode}/agent/runs`
2. `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}`
3. Agent run 创建后会调用 Python Runtime。
4. Java 会持久化 steps/actions。
5. 有待确认 action 时，run 进入 `WAITING_CONFIRMATION`。
6. Day 8 已完成 `document.indexing_task.retry` 的确认执行。
7. Day 9 已完成 `embedding.rebuild.submit` 的确认执行。

前端现状：

1. `rag-frontend` 已有 React + TypeScript + Vite + Ant Design + TanStack Query。
2. 已有统一 API client：
   - `src/api/client.ts`
3. 已有统一响应类型：
   - `src/types/api.ts`
4. 已有当前知识库路由与状态：
   - `src/hooks/use-current-kb.ts`
   - `/kb/:kbCode/...`
5. 已有侧边导航：
   - `src/components/app-shell/app-shell.tsx`
6. 已有页面路由：
   - `src/app/router.tsx`
7. 已有错误展示组件：
   - `src/components/feedback/api-error-alert.tsx`

Day 10 应复用这些前端基础设施，不新建独立前端框架或另类请求封装。

## 关键边界

Day 10 必须继续遵守：

1. Java 是 Agent Run 状态中心。
2. 前端只调用 Java API。
3. 前端不直接调用 Python Runtime。
4. 前端不生成 `runCode / stepCode / actionCode`。
5. 前端不自行推断写操作是否成功，只展示 Java 返回的 run/action 状态。
6. Day 10 不实现 confirm/reject 操作按钮。
7. Day 10 不做复杂 timeline，steps 先用表格或列表展示。
8. Day 10 不新增后端接口。

## 页面定位

新增页面：

```text
/kb/:kbCode/agent
```

导航名称建议：

```text
Agent 诊断
```

页面定位：

1. 面向运维/开发调试，不是普通聊天入口。
2. 首屏就是可用工作台，不做营销式说明页。
3. 页面信息密度应接近当前后台风格，保持克制。
4. 重点让用户看到 Agent 对当前知识库做了什么检查、得出什么结论、推荐了什么动作。

## 用户流程

### 1. 进入页面

用户进入：

```text
/kb/day20-cn-kb/agent
```

页面展示：

1. 当前知识库编码。
2. 诊断目标输入框。
3. 可选问题输入框。
4. 运行模式选择。
5. 创建 run 按钮。

默认值建议：

```text
goal = 诊断这个知识库为什么不能问答
question = 第二百三十八条是什么
runMode = DIAGNOSE_AND_RECOMMEND
createdBy = frontend
```

也可以提供两个快捷目标按钮：

1. `诊断问答 readiness`
2. `检查索引任务异常`

Day 10 快捷按钮只负责填充表单，不负责自动执行。

### 2. 创建 run

调用：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs
```

请求体：

```json
{
  "goal": "诊断这个知识库为什么不能问答",
  "question": "第二百三十八条是什么",
  "runMode": "DIAGNOSE_AND_RECOMMEND",
  "createdBy": "frontend"
}
```

成功后：

1. 保存返回的 `runCode` 到页面状态。
2. 立即展示返回的 run 详情。
3. 也可以调用一次 `GET /runs/{runCode}` 刷新，但 Day 10 可以直接使用 create response。

### 3. 查询 run

页面提供 runCode 查询输入框或“刷新”按钮。

调用：

```text
GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}
```

用途：

1. 允许演示时刷新 run 状态。
2. 为 Day 11 timeline 和 Day 12 confirm/reject 刷新做基础。
3. 避免用户离开页面后无法恢复刚才 run。

Day 10 不要求持久化最近 run 到 localStorage。可以后续再补。

## 数据模型

建议新增：

```text
rag-frontend/src/types/agent.ts
```

类型：

```text
AgentRunMode = "DIAGNOSE_ONLY" | "DIAGNOSE_AND_RECOMMEND"
AgentRunStatus = "RUNNING" | "WAITING_CONFIRMATION" | "SUCCEEDED" | "FAILED"
AgentStepType = "NODE" | "TOOL_CALL" | "LLM_CALL"
AgentStepStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED"
AgentActionRiskLevel = "LOW" | "MEDIUM" | "HIGH"
AgentActionStatus = "PENDING_CONFIRMATION" | "CONFIRMED" | "EXECUTING" | "SUCCEEDED" | "FAILED" | "REJECTED"
```

核心 response：

```text
AgentRun
AgentStep
AgentAction
AgentRunCreatePayload
```

字段按后端 response 对齐，不做前端私有重命名。

## API 封装

建议新增：

```text
rag-frontend/src/api/agent.ts
```

方法：

```text
createAgentRun(kbCode, payload)
getAgentRun(kbCode, runCode)
```

Day 10 不封装：

```text
confirmAgentAction
rejectAgentAction
```

这两个留到 Day 12。

## 页面设计

建议新增：

```text
rag-frontend/src/pages/agent/index.tsx
```

页面分区：

1. 顶部操作区：
   - 诊断目标
   - 可选问题
   - runMode
   - createdBy
   - 创建按钮
2. 查询区：
   - runCode 输入
   - 查询按钮
   - 刷新按钮
3. 结果概览：
   - runCode
   - status
   - runMode
   - createdBy
   - createdAt / finishedAt
   - errorMessage
4. Summary：
   - 展示 `summary`
   - 如果为空，展示空态
5. Steps：
   - 表格列：
     - nodeName
     - toolName
     - stepType
     - status
     - durationMs
     - errorMessage
6. Actions：
   - 表格列：
     - actionCode
     - toolName
     - title
     - riskLevel
     - requiresConfirmation
     - status
     - errorMessage

Day 10 的 Actions 只展示，不提供 confirm/reject 按钮。

## 组件选择

复用 Ant Design：

1. `Form`
2. `Input`
3. `Select`
4. `Button`
5. `Card`
6. `Descriptions`
7. `Table`
8. `Tag`
9. `Alert`
10. `Space`

状态展示：

1. Agent run status 用 `Tag`。
2. action risk 用 `Tag`。
3. action status 用 `Tag`。
4. errorMessage 用 `Alert` 或表格列展示。

如果现有 `StatusBadge` 能自然复用，可以复用；如果需要写大量映射，Day 10 先在页面内用小函数处理。

## 路由与导航

修改：

```text
rag-frontend/src/app/router.tsx
```

新增懒加载页面：

```text
const AgentPage = lazy(() =>
  import("../pages/agent").then((module) => ({ default: module.AgentPage }))
);
```

新增路由：

```text
{ path: "kb/:kbCode/agent", element: withPageLoader(<AgentPage />) }
```

修改：

```text
rag-frontend/src/components/app-shell/app-shell.tsx
```

新增侧边栏项：

```text
Agent 诊断
```

图标可使用 Ant Design 已有图标，例如：

```text
RobotOutlined
```

如果当前版本图标不可用，可以使用 `RadarChartOutlined` 或 `ExperimentOutlined`。

## 错误处理

Day 10 必须复用：

```text
ApiErrorAlert
```

创建 run 或查询 run 失败时展示：

1. message
2. requestId

不要只用 `message.error(...)`，否则演示时丢失后端 requestId。

## 测试与验证

Day 10 完成后建议执行：

```text
cd rag-frontend
npm run build
```

如果后端和 Python 服务已运行，可做手工验证：

1. 进入 `/kb/{kbCode}/agent`。
2. 使用 `诊断这个知识库为什么不能问答` 创建 run。
3. 页面显示 `summary`。
4. 页面显示 steps。
5. 页面显示 `embedding.rebuild.submit` action。
6. 使用 `检查这个知识库有没有索引异常` 创建 run。
7. 页面显示 `document.indexing_task.retry` action。
8. 输入已存在 runCode 后能查询 run。

如果本地没有完整服务，Day 10 至少保证：

1. TypeScript 编译通过。
2. Vite build 通过。
3. 页面路由能被构建。

## 验收标准

Day 10 完成时应满足：

1. 侧边栏出现 `Agent 诊断`。
2. `/kb/:kbCode/agent` 页面可访问。
3. 页面可创建 Agent run。
4. 页面可查询指定 Agent run。
5. 页面展示 run status。
6. 页面展示 summary。
7. 页面展示 steps 基础表格。
8. 页面展示 actions 基础表格。
9. 页面错误展示包含 requestId。
10. 不实现 confirm/reject 按钮。
11. 不直接调用 Python Runtime。
12. `npm run build` 通过。
13. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 10 暂不做：

1. timeline 视觉化。
2. action 推荐卡片。
3. confirm/reject UI。
4. 执行结果刷新闭环。
5. `qa.retrieve.probe`。
6. Python -> Java 真实工具 HTTP API。
7. localStorage 保存最近 run。
8. 前端页面级自动化测试。

## 下一步

Day 11 做 timeline 和推荐动作卡片：

```text
steps 基础表格
  -> timeline 轨迹
actions 基础表格
  -> 推荐动作卡片
```

Day 12 再接入 confirm/reject UI 和执行结果刷新。

## Day 10 执行记录

实际完成内容：

1. 新增前端 Agent 类型：
   - `rag-frontend/src/types/agent.ts`
2. 新增前端 Agent API：
   - `rag-frontend/src/api/agent.ts`
3. 新增 Agent 工作台页面：
   - `rag-frontend/src/pages/agent/index.tsx`
4. 新增路由：
   - `/kb/:kbCode/agent`
5. 新增侧边栏入口：
   - `Agent 诊断`
6. 页面已支持：
   - 创建 Agent run
   - 查询指定 Agent run
   - 刷新当前 run
   - 展示 run 概览
   - 展示 summary
   - 展示 steps 基础表格
   - 展示 actions 基础表格
7. 页面错误展示复用 `ApiErrorAlert`。
8. Day 10 未实现 confirm/reject UI，保留到 Day 12。

修改或新增的关键文件：

1. `rag-frontend/src/types/agent.ts`
2. `rag-frontend/src/api/agent.ts`
3. `rag-frontend/src/pages/agent/index.tsx`
4. `rag-frontend/src/app/router.tsx`
5. `rag-frontend/src/components/app-shell/app-shell.tsx`
6. `docs/work/rag-agent/current-status.md`
7. `docs/work/rag-agent/README.md`
8. `docs/work/rag-agent/work day10.md`

已验证：

```text
cd rag-frontend
npm run build
```
