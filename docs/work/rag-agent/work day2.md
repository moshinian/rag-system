# Day 2：Agent 查询 API 与 Service 骨架

## 目标

Day 2 的目标是让 Day 1 落下的 `agent_run / agent_step / agent_action` 模型具备最小 API 承载层，为后续 Java 调用 Python Agent Runtime 做准备。

本日完成：

1. Java 新增 Agent 请求/响应对象。
2. Java 新增 `AgentRunService` 骨架。
3. Java 新增 `AgentController` 查询与创建入口。
4. 支持创建 Agent run 和查询 run 详情。
5. 查询 run 详情时返回 steps 和 actions。
6. 继续保持 Java 统一生成 `runCode / stepCode / actionCode` 的边界。

Day 2 不实现 Python LangGraph 调用，不实现 health/readiness 工具节点，也不实现前端页面。

## 当前输入

Day 1 已完成：

1. `V18__create_agent_tables.sql`
2. `AgentRunEntity / AgentStepEntity / AgentActionEntity`
3. `AgentRunRepository / AgentStepRepository / AgentActionRepository`
4. Agent 状态枚举
5. Java / Python Runtime 协议 DTO
6. Python `AgentState` 草案

Day 2 应直接复用这些基础设施，不重新设计表结构和状态模型。

## 已完成

### 请求对象

新增：

1. `AgentRunCreateRequest`

字段：

```text
goal: String, 必填
question: String, 可选
runMode: AgentRunMode, 可选，默认 DIAGNOSE_AND_RECOMMEND
createdBy/operator: String, 可选，默认 system 或沿用现有 operator 风格
```

当前约束：

1. `goal` 必须非空。
2. `runMode` 只允许 `DIAGNOSE_ONLY / DIAGNOSE_AND_RECOMMEND`。
3. Day 2 只创建 run，不实际触发 Python Agent Runtime。

### 响应对象

新增：

1. `AgentRunResponse`
2. `AgentStepResponse`
3. `AgentActionResponse`

`AgentRunResponse` 至少包含：

```text
runCode
knowledgeBaseCode
goal
question
runMode
status
summary
errorMessage
steps
actions
createdBy
createdAt
updatedAt
finishedAt
```

`AgentStepResponse` 至少包含：

```text
stepCode
nodeName
toolName
stepType
status
inputJson
outputJson
durationMs
errorMessage
startedAt
finishedAt
createdAt
updatedAt
```

`AgentActionResponse` 至少包含：

```text
actionCode
toolName
title
reason
riskLevel
requiresConfirmation
status
actionPayload
confirmedBy
confirmedAt
executedAt
resultJson
errorMessage
createdAt
updatedAt
```

Day 2 暂不需要分页列表接口。先做创建和详情查询即可。

### AgentRunService

已实现职责：

1. 校验知识库存在。
2. 创建 `AgentRunEntity`。
3. 使用 `SnowflakeIdGenerator` 生成：
   - `id`
   - `runCode`
4. 设置初始状态：
   - `RUNNING`
5. 查询 run 详情：
   - 读取 `agent_run`
   - 读取同 runCode 下的 `agent_step`
   - 读取同 runCode 下的 `agent_action`
   - 组装 `AgentRunResponse`

Day 2 不要在 `createRun` 里调用 Python Runtime。后续 Day 5 再接：

```text
Java create run
→ call Python Agent Runtime
→ persist returned steps/actions
→ decide run status
```

### 编码生成原则

当前已使用编码前缀：

```text
runCode: AR-
stepCode: AST-
actionCode: ACT-
```

Day 2 只生成 `runCode`。`stepCode / actionCode` 在 Day 5 持久化 Python 返回结果时生成。

### AgentController

路径：

```text
/api/knowledge-bases/{kbCode}/agent
```

已实现：

```text
POST /runs
GET  /runs/{runCode}
```

接口完整路径：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs
GET  /api/knowledge-bases/{kbCode}/agent/runs/{runCode}
```

确认/拒绝接口先预留到 Day 8 之后实现，不在 Day 2 做：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject
```

## 关键边界

Day 2 必须继续遵守：

1. Java 是 Agent Run 的权威状态中心。
2. Python 不生成 `runCode / stepCode / actionCode`。
3. Day 2 不接 LangGraph，不做假 Agent 执行。
4. `WAITING_CONFIRMATION` 不在 Day 2 使用，只有后续存在推荐动作时才由 Java 判定。
5. 写操作确认执行不在 Day 2 实现。
6. 不把 Agent 能力写进根 README 的已完成列表。

## 已验证

已执行：

1. `mvn -q -pl rag-backend -DskipTests compile`
2. `mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest test`

新增测试：

1. `AgentRunServiceTest`
   - 创建 run 时生成 `runCode`
   - 初始状态为 `RUNNING`
   - run 绑定正确知识库
   - 查询详情能返回空 steps/actions
   - 跨知识库查询会拒绝
   - 知识库不存在时不会创建 run
2. `AgentControllerTest`
   - `POST /api/knowledge-bases/{kbCode}/agent/runs` 返回 `202 Accepted`
   - `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}` 返回 run 详情
   - 响应包含 requestId、runCode、status、steps/actions 数组

## 验收结果

1. 后端可编译。
2. Agent run 可创建。
3. Agent run 可按 `runCode` 查询。
4. 查询结果包含 `steps` 和 `actions` 数组。
5. Day 2 未接入 Python Runtime，符合计划边界。
6. Day 2 未实现 confirm/reject，保留到 Day 8 之后。

## 下一步

Day 3 将进入 P0 只读工具封装：

1. `system.health.check`
2. `kb.readiness.check`

Day 3 的重点不是 Agent Runtime，而是先把 Java 侧可控工具边界建立起来。
