# Day 11：Agent timeline 与推荐动作卡片计划

## 目标

Day 11 的目标是在 Day 10 前端 Agent 工作台基础上，把 steps/actions 从“基础表格”升级为更适合演示和讲解的视图：

```text
steps 表格
  -> Agent 执行 timeline

actions 表格
  -> 推荐动作卡片
```

Day 11 只做展示升级，不接入 confirm/reject UI。确认和拒绝操作留到 Day 12。

## 当前输入

Day 10 已完成：

1. 新增 `/kb/:kbCode/agent` 页面。
2. 页面可创建 Agent run。
3. 页面可查询 Agent run。
4. 页面可刷新当前 run。
5. 页面已展示：
   - run 概览
   - summary
   - steps 基础表格
   - actions 基础表格
6. 前端已新增：
   - `rag-frontend/src/types/agent.ts`
   - `rag-frontend/src/api/agent.ts`
   - `rag-frontend/src/pages/agent/index.tsx`
7. 前端已通过：

```text
npm run build
```

后端能力：

1. Java 返回完整 `AgentRunResponse`。
2. `steps` 包含：
   - stepCode
   - nodeName
   - toolName
   - stepType
   - status
   - inputJson
   - outputJson
   - durationMs
   - errorMessage
   - startedAt / finishedAt
3. `actions` 包含：
   - actionCode
   - toolName
   - title
   - reason
   - riskLevel
   - requiresConfirmation
   - status
   - actionPayload
   - resultJson
   - errorMessage
   - confirmedBy / confirmedAt / executedAt

## 关键边界

Day 11 必须继续遵守：

1. 前端只展示 Java 返回状态。
2. 前端不直接调用 Python Runtime。
3. 前端不生成 `runCode / stepCode / actionCode`。
4. 前端不执行写操作。
5. Day 11 不实现 confirm/reject 按钮。
6. Day 11 不新增后端接口。
7. Day 11 不做 `qa.retrieve.probe`。
8. Day 11 不把 Agent 页面做成聊天页。

## 展示目标

Day 11 完成后，用户进入 Agent 工作台并创建 run 后，应能直观看到：

1. Agent 执行了哪些节点。
2. 哪些节点调用了工具。
3. 每个节点成功、失败或跳过。
4. 每个节点大概耗时。
5. Agent 推荐了哪些动作。
6. 推荐动作为什么需要执行。
7. 动作风险等级和确认要求。
8. 当前动作状态。

这会让 Day 10 的“能用”变成 Day 11 的“可讲、可演示”。

## 页面结构调整

当前 Day 10 页面结构：

```text
Agent 诊断表单
查询 Run
运行概览
诊断摘要
Steps 表格
Actions 表格
```

Day 11 建议调整为：

```text
Agent 诊断表单
查询 Run
运行概览
诊断摘要
执行轨迹 Timeline
推荐动作 Cards
原始 Steps 表格
原始 Actions 表格
```

说明：

1. Timeline 和 Cards 作为主展示。
2. 原始表格继续保留，便于调试和核对字段。
3. 不要把表格删除，否则调试信息反而变少。

## Timeline 设计

### 组件形态

可以先在页面内实现小组件：

```text
AgentStepTimeline
```

如果页面变得过长，可以拆到：

```text
rag-frontend/src/components/agent/agent-step-timeline.tsx
```

Day 11 推荐拆组件，避免 `pages/agent/index.tsx` 继续膨胀。

### UI 组件

优先使用 Ant Design：

```text
Timeline
Tag
Typography
Collapse
Descriptions
```

### Timeline item 内容

每个 step 展示：

1. nodeName
2. stepType
3. status
4. toolName
5. durationMs
6. errorMessage

建议主标题：

```text
{nodeName}
```

副信息：

```text
stepType / toolName / durationMs
```

状态颜色建议：

```text
SUCCEEDED -> green
FAILED -> red
RUNNING -> blue
SKIPPED -> gray
```

### JSON 展示

Day 11 可增加折叠区查看：

1. `inputJson`
2. `outputJson`

建议使用：

```text
<pre>
```

并限制高度，避免长 JSON 撑爆页面：

```text
maxHeight: 240
overflow: auto
```

如果 JSON 为空则不展示。

### 节点名称映射

可以保留原始 nodeName，不强行翻译。

可选轻量映射：

```text
parse_goal -> 解析目标
system_health_check -> 系统健康检查
kb_readiness_check -> 问答 readiness
documents_status_scan -> 文档状态扫描
indexing_tasks_scan -> 索引任务扫描
diagnose -> 诊断归因
recommend_actions -> 生成推荐动作
generate_report -> 生成报告
```

Day 11 如果时间紧，先用原始 nodeName，避免映射不全造成误导。

## 推荐动作卡片设计

### 组件形态

可以先在页面内实现小组件：

```text
AgentActionCards
```

推荐拆到：

```text
rag-frontend/src/components/agent/agent-action-cards.tsx
```

### UI 组件

优先使用 Ant Design：

```text
Card
Tag
Descriptions
Typography
Collapse
Alert
Space
```

### 卡片内容

每个 action 展示：

1. title
2. toolName
3. actionCode
4. riskLevel
5. requiresConfirmation
6. status
7. reason
8. actionPayload
9. resultJson
10. errorMessage
11. confirmedBy / confirmedAt / executedAt

### 风险展示

颜色建议：

```text
LOW -> green
MEDIUM -> orange
HIGH -> red
```

`requiresConfirmation=true` 时展示：

```text
需要人工确认
```

但 Day 11 不显示确认按钮。

### 状态展示

颜色建议：

```text
PENDING_CONFIRMATION -> gold
EXECUTING -> blue
SUCCEEDED -> green
FAILED -> red
REJECTED -> default
```

### 操作占位

Day 11 可以展示一个提示：

```text
确认执行入口将在下一阶段接入
```

但不要做不可用按钮。不可用按钮容易让用户误以为功能坏了。

## 文件计划

建议新增：

```text
rag-frontend/src/components/agent/agent-step-timeline.tsx
rag-frontend/src/components/agent/agent-action-cards.tsx
```

建议修改：

```text
rag-frontend/src/pages/agent/index.tsx
docs/work/rag-agent/current-status.md
docs/work/rag-agent/README.md
docs/work/rag-agent/work day11.md
```

可选修改：

```text
rag-frontend/src/styles/global.css
```

只有当 JSON pre 或卡片布局需要统一样式时才改 CSS。优先使用内联样式或 Ant Design props，避免扩大样式影响面。

## 实现步骤

### Step 1：抽出状态展示 helper

当前 `pages/agent/index.tsx` 内已有：

```text
renderRunStatus
renderStepStatus
renderActionStatus
renderRisk
```

Day 11 可以继续放在页面内，也可以迁移到组件文件里。

建议：

1. Timeline 和 Action Cards 内各自保留轻量 helper。
2. 不急着抽公共 util，避免过早抽象。

### Step 2：新增 AgentStepTimeline

输入：

```text
steps: AgentStep[]
```

输出：

1. 空态：`暂无执行轨迹`
2. Timeline item 列表。
3. 可展开 JSON 输入输出。

### Step 3：新增 AgentActionCards

输入：

```text
actions: AgentAction[]
```

输出：

1. 空态：`暂无推荐动作`
2. 每个 action 一张卡片。
3. 展示风险、状态、确认要求和 reason。
4. 可展开 payload/result。

### Step 4：接入 AgentPage

在 Day 10 页面中：

```text
诊断摘要
执行轨迹
推荐动作
Steps 表格
Actions 表格
```

Timeline 和 Cards 放在表格前。

### Step 5：构建验证

执行：

```text
cd rag-frontend
npm run build
```

## 验证建议

如果本地后端和 Python Runtime 可用，手工验证：

1. 进入 `/kb/{kbCode}/agent`。
2. 创建 readiness 诊断 run。
3. Timeline 展示 `parse_goal -> ... -> generate_report`。
4. 推荐动作卡片展示 `embedding.rebuild.submit`。
5. 创建索引异常诊断 run。
6. 推荐动作卡片展示 `document.indexing_task.retry`。
7. 展开 payload 能看到 `kbCode/taskId/documentCode` 等字段。
8. 不出现 confirm/reject 按钮。

如果本地完整服务不可用，至少验证：

1. TypeScript 编译通过。
2. Vite build 通过。
3. 页面导入组件无循环依赖。

## 验收标准

Day 11 完成时应满足：

1. Agent 页面展示执行轨迹 timeline。
2. Timeline 能展示每个 step 的 nodeName/status/type/toolName/duration。
3. Timeline 可查看 step input/output JSON。
4. Agent 页面展示推荐动作卡片。
5. 动作卡片展示 title/toolName/risk/status/reason。
6. 动作卡片可查看 actionPayload/resultJson。
7. 动作卡片展示 requiresConfirmation。
8. 不提供 confirm/reject 按钮。
9. 原始 steps/actions 表格仍保留。
10. 前端不直接调用 Python Runtime。
11. 不新增后端接口。
12. `npm run build` 通过。
13. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 11 暂不做：

1. confirm/reject UI。
2. action 执行后自动刷新。
3. action 执行结果轮询。
4. 多 action 并发确认状态处理。
5. `qa.retrieve.probe`。
6. Python -> Java 真实工具 HTTP API。
7. 后端接口修改。
8. 页面级自动化测试。

## 下一步

Day 12 接入 confirm/reject UI：

```text
推荐动作卡片
  -> confirm/reject 按钮
  -> 调用 Java confirm/reject API
  -> 刷新 run
  -> 展示执行结果
```

Day 12 的关键仍是 human-in-the-loop：前端只能触发 Java confirm/reject，不能绕过 Java 直接执行写操作。

## Day 11 执行记录

实际完成内容：

1. 新增 `AgentStepTimeline`：
   - `rag-frontend/src/components/agent/agent-step-timeline.tsx`
2. 新增 `AgentActionCards`：
   - `rag-frontend/src/components/agent/agent-action-cards.tsx`
3. `AgentStepTimeline` 已展示：
   - nodeName
   - stepType
   - status
   - toolName
   - durationMs
   - errorMessage
   - inputJson / outputJson 折叠查看
4. `AgentActionCards` 已展示：
   - title
   - toolName
   - actionCode
   - riskLevel
   - requiresConfirmation
   - status
   - reason
   - actionPayload / resultJson 折叠查看
   - confirmedBy / confirmedAt / executedAt
5. Agent 页面展示顺序已调整为：
   - 运行概览
   - 诊断摘要
   - 执行轨迹
   - 推荐动作
   - 原始 Steps
   - 原始 Actions
6. Day 11 没有接入 confirm/reject UI。
7. Day 11 没有新增后端接口。

修改或新增的关键文件：

1. `rag-frontend/src/components/agent/agent-step-timeline.tsx`
2. `rag-frontend/src/components/agent/agent-action-cards.tsx`
3. `rag-frontend/src/pages/agent/index.tsx`
4. `docs/work/rag-agent/current-status.md`
5. `docs/work/rag-agent/README.md`
6. `docs/work/rag-agent/work day11.md`

已验证：

```text
cd rag-frontend
npm run build
```
