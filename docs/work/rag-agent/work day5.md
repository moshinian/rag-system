# Day 5：Java 调用 Python Runtime 并落库

## 目标

Day 5 的目标是把 Day 2 的 Java Agent run 骨架和 Day 4 的 Python LangGraph Runtime 串起来：

```text
Java create agent_run
  -> call Python POST /v1/agent/runs
  -> receive steps/actions
  -> Java generate stepCode/actionCode
  -> persist agent_step/agent_action
  -> update agent_run status
```

Day 5 的重点是 Java 重新成为 Agent Run 状态中心。Python 仍然只返回 Runtime 计算结果，不生成最终业务编码，不写库，不执行写操作。

本日已完成：

1. Java 新增 Python Agent Runtime 调用能力。
2. `AgentRunService.createRun` 创建 run 后同步调用 Python Runtime。
3. Java 持久化 Python 返回的 steps。
4. Java 持久化 Python 返回的 recommendedActions。
5. Java 统一生成 `stepCode / actionCode`。
6. Java 根据 Runtime 结果更新 `agent_run.status`。
7. 保持 Day 5 不实现 confirm/reject，不执行推荐动作。

## 当前输入

Day 1 已完成：

1. `agent_run / agent_step / agent_action` 三张表。
2. Agent 状态枚举。
3. Java Runtime 协议 DTO：
   - `AgentRuntimeRequest`
   - `AgentRuntimeResponse`
   - `AgentRuntimeStepResult`
   - `AgentRuntimeActionDraft`

Day 2 已完成：

1. `AgentRunService.createRun(...)` 目前只创建 `RUNNING` 状态 run。
2. `AgentRunService.getRun(...)` 已能组装 steps/actions。
3. `AgentController` 已暴露：
   - `POST /api/knowledge-bases/{kbCode}/agent/runs`
   - `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}`

Day 3 已完成：

1. Java 侧 P0 只读工具抽象和注册表。
2. `system.health.check`
3. `kb.readiness.check`

Day 4 已完成：

1. Python `POST /v1/agent/runs`。
2. LangGraph 最小图。
3. Runtime 返回 steps/actions/summary。
4. `reembedRequired=true` 时返回 `embedding.rebuild.submit` action 草案。
5. Python 不返回 `stepCode / actionCode`。

## 关键边界

Day 5 必须继续遵守：

1. Java 是 Agent Run 状态中心。
2. Java 统一生成 `runCode / stepCode / actionCode`。
3. Java 负责落库和审计轨迹。
4. Python 只返回 Runtime 结果，不写库。
5. Python 返回的 action 只是草案，不能直接执行。
6. Day 5 只让 run 进入 `WAITING_CONFIRMATION`，不执行 `embedding.rebuild.submit`。
7. Day 5 不实现 confirm/reject。
8. Day 5 不实现 `document.indexing_task.retry`。
9. Day 5 不接前端。
10. Day 5 不把 Static Tool Client 误写成真实 Java 工具调用闭环；Python 仍可能使用 Day 4 的替身工具结果。

## 已新增或修改文件

已新增：

```text
rag-backend/src/main/java/com/example/rag/integration/agent/AgentRuntimeClient.java
rag-backend/src/test/java/com/example/rag/integration/agent/AgentRuntimeClientTest.java
```

已修改：

```text
rag-backend/src/main/java/com/example/rag/config/RagAiGatewayProperties.java
rag-backend/src/main/resources/application.yml
rag-backend/src/main/java/com/example/rag/service/AgentRunService.java
rag-backend/src/test/java/com/example/rag/service/AgentRunServiceTest.java
rag-backend/src/test/java/com/example/rag/controller/AgentControllerTest.java
```

当前选择新增 `AgentRuntimeClient`，原因是：

1. Agent Runtime 是编排能力，不是 embedding/chat provider 能力。
2. 后续 Agent Runtime 可能需要独立日志、错误映射和路径配置。
3. 可以避免让 `AiGatewayClient` 继续膨胀。

## 配置设计

当前已有：

```text
rag.ai.gateway.base-url
rag.ai.gateway.embeddings-path
rag.ai.gateway.chat-completions-path
rag.ai.gateway.connect-timeout-millis
rag.ai.gateway.read-timeout-millis
```

Day 5 已新增：

```text
rag.ai.gateway.agent-runs-path=/v1/agent/runs
```

`RagAiGatewayProperties` 已新增字段：

```text
agentRunsPath
```

默认值：

```text
/v1/agent/runs
```

Day 5 未新增独立 agent base-url，继续复用 `rag-ai-service` 当前 baseUrl。

## AgentRuntimeClient 设计

职责：

1. 接收 `AgentRuntimeRequest`。
2. POST 到 Python `POST /v1/agent/runs`。
3. 反序列化为 `AgentRuntimeResponse`。
4. 透传 `X-Request-Id`。
5. 将 HTTP、序列化、空响应等异常转换为 `BusinessException` 或由 `AgentRunService` 捕获。

已实现方法：

```text
AgentRuntimeResponse run(AgentRuntimeRequest request)
```

已补结构化日志：

1. 成功：
   - `agent.runtime.completed`
   - `runCode`
   - `status`
   - `stepCount`
   - `actionCount`
2. 失败：
   - `agent.runtime.failed`
   - `runCode`
   - `message`

Day 5 未实现复杂重试。Agent Runtime 是诊断请求的一部分，失败后 run 会被标记为 `FAILED`，避免静默重试造成重复步骤。

## AgentRunService 改造

当前 `createRun`：

```text
校验知识库
创建 agent_run RUNNING
返回 run 详情
```

Day 5 已改为：

```text
校验知识库
创建 agent_run RUNNING
调用 Python Runtime
持久化 steps/actions
更新 agent_run summary/status/errorMessage/finishedAt
返回最新 run 详情
```

已拆分私有方法：

```text
buildRuntimeRequest(...)
persistRuntimeSteps(...)
persistRuntimeActions(...)
resolveFinalRunStatus(...)
markRunFailed(...)
```

### Runtime Request

Java 发送给 Python：

```text
runCode = Java 生成的 runCode
kbCode = 当前知识库 kbCode
goal = request.goal
question = request.question
runMode = run.runMode
```

注意：

1. `runCode` 由 Java 生成。
2. `kbCode` 使用 path variable 对应的知识库编码。
3. Python 不需要 knowledgeBaseId。

### Step 落库

Python 返回的 `AgentRuntimeStepResult` 转成 `AgentStepEntity`：

```text
id = snowflake
runCode = run.runCode
stepCode = AST-...
nodeName = runtimeStep.nodeName
toolName = runtimeStep.toolName
stepType = runtimeStep.stepType
status = runtimeStep.status
inputJson = runtimeStep.inputJson
outputJson = runtimeStep.outputJson
durationMs = runtimeStep.durationMs
errorMessage = runtimeStep.errorMessage
startedAt = now
finishedAt = now
```

Day 5 可以先把 `startedAt / finishedAt` 都设为 Java 落库时间，因为 Python Runtime response 当前没有节点开始结束时间。

### Action 落库

Python 返回的 `AgentRuntimeActionDraft` 转成 `AgentActionEntity`：

```text
id = snowflake
runCode = run.runCode
actionCode = ACT-...
toolName = draft.toolName
title = draft.title
reason = draft.reason
riskLevel = draft.riskLevel
requiresConfirmation = draft.requiresConfirmation
status = PENDING_CONFIRMATION
actionPayload = draft.actionPayload
```

Day 5 只落库，不执行。

Action 状态建议：

1. `requiresConfirmation=true`：`PENDING_CONFIRMATION`
2. `requiresConfirmation=false`：Day 5 也先不执行，可暂定仍不生成写动作或保持 `PENDING_CONFIRMATION`

为了边界清晰，Day 5 建议规定：Python 推荐动作一律必须 `requiresConfirmation=true`，Java 若收到 `requiresConfirmation=false` 的写动作，应先拒绝或按 `PENDING_CONFIRMATION` 保守处理。

### Run 状态收口

Runtime 成功返回后：

```text
if response.status == FAILED:
    agent_run.status = FAILED
    error_message = response.errorMessage
    finished_at = now
else if any action.requiresConfirmation == true:
    agent_run.status = WAITING_CONFIRMATION
    summary = response.summary
    finished_at = null 或 now 二选一
else:
    agent_run.status = SUCCEEDED
    summary = response.summary
    finished_at = now
```

Day 5 当前选择：

1. `WAITING_CONFIRMATION`：`finishedAt = null`
2. `SUCCEEDED / FAILED`：`finishedAt = now`

理由：

1. `WAITING_CONFIRMATION` 仍处于人机协同等待态。
2. 后续 confirm/reject 或 action 执行完成后，再决定最终完成时间。

Runtime 调用抛异常时：

```text
agent_run.status = FAILED
agent_run.errorMessage = "Failed to call Agent Runtime: ..."
agent_run.finishedAt = now
```

并且返回最新 run 详情，而不是让已创建的 run 永远停在 `RUNNING`。

## 事务边界

Day 5 不建议在一个长事务里包住外部 HTTP 调用。

当前实现顺序：

1. 事务 A：创建 `agent_run RUNNING` 并提交。
2. 非事务：调用 Python Runtime。
3. 事务 B：持久化 steps/actions，更新 run 状态。

原因：

1. 外部 HTTP 调用不应占用数据库事务。
2. Runtime 慢或失败时，不应长时间持有数据库连接和锁。
3. run 已创建后，即使 Runtime 失败，也能落库失败状态供前端查询。

当前 `createRun` 没有 `@Transactional`，避免把 Python Runtime HTTP 调用包在长事务中。

## API 行为

`POST /api/knowledge-bases/{kbCode}/agent/runs` Day 5 后仍返回 `202 Accepted`。

但响应体从“只有 RUNNING 空详情”变为“Runtime 调用后的最新详情”：

1. 正常发现 `reembedRequired=true`：
   - `status = WAITING_CONFIRMATION`
   - `summary` 有诊断结论
   - `steps` 非空
   - `actions` 包含 `embedding.rebuild.submit`
2. Runtime 返回无 action：
   - `status = SUCCEEDED`
   - `summary` 有诊断结论
   - `steps` 非空
   - `actions` 为空
3. Runtime 调用失败：
   - `status = FAILED`
   - `errorMessage` 有失败原因
   - `steps/actions` 可能为空

## 已验证

### AgentRunServiceTest

`AgentRunServiceTest` 已覆盖：

1. 创建 run 后会调用 `AgentRuntimeClient.run(...)`。
2. Runtime 返回 steps 后，Java 生成 `AST-` stepCode 并落库。
3. Runtime 返回 action 后，Java 生成 `ACT-` actionCode 并落库。
4. Runtime 返回 `embedding.rebuild.submit` 且 requiresConfirmation=true 时：
   - action status = `PENDING_CONFIRMATION`
   - run status = `WAITING_CONFIRMATION`
5. Runtime 返回成功但无 action 时：
   - run status = `SUCCEEDED`
   - finishedAt 非空
6. Runtime 返回 `FAILED` 时：
   - run status = `FAILED`
   - errorMessage 落库
7. Runtime client 抛异常时：
   - run status = `FAILED`
   - 已创建 run 不丢失
8. Python 返回的 action 不包含 actionCode，Java 仍生成 `ACT-`。

### AgentControllerTest

建议调整：

1. `POST /agent/runs` 仍返回 `202 Accepted`。
2. 响应中包含 steps/actions。
3. 有 action 时响应 status 为 `WAITING_CONFIRMATION`。
4. 不暴露 Python 生成 code 的假象。

### AgentRuntimeClientTest

`AgentRuntimeClientTest` 已覆盖：

1. 请求 path 为 `/v1/agent/runs`。
2. 请求体字段为 camelCase：
   - `runCode`
   - `kbCode`
   - `runMode`
3. 响应能反序列化 `steps/recommendedActions`。
4. 非 2xx 响应转换为明确异常。
5. `X-Request-Id` 透传。

## 验证命令

Day 5 已执行：

```text
mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest test
mvn -q -pl rag-backend -DskipTests compile
```

```text
mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest,AgentRuntimeClientTest test
```

## 验收结果

Day 5 已满足：

1. Java 创建 run 后能调用 Python Runtime。
2. Java 能把 Python steps 落入 `agent_step`。
3. Java 能把 Python recommendedActions 落入 `agent_action`。
4. Java 生成 `AST-` 和 `ACT-` 编码。
5. Python response 不需要也不能提供 `stepCode/actionCode`。
6. 有待确认 action 时，run 进入 `WAITING_CONFIRMATION`。
7. 无待确认 action 时，run 进入 `SUCCEEDED`。
8. Runtime 失败时，run 进入 `FAILED` 且有 `errorMessage`。
9. Day 5 不执行任何写操作。
10. Day 5 不实现 confirm/reject。
11. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 5 暂不做：

1. Python 调用 Java 真实工具 HTTP API。
2. `documents.status.scan`。
3. `indexing.tasks.scan`。
4. `document.indexing_task.retry`。
5. `embedding.rebuild.submit` 真实执行。
6. confirm/reject。
7. 前端 Agent 工作台。
8. `qa.retrieve.probe`。
9. LLM 润色报告。

## 下一步

Day 6 进入 P1 只读扫描工具：

1. Java 封装 `documents.status.scan`。
2. Java 封装 `indexing.tasks.scan`。
3. Python LangGraph 扩展节点：
   - `documents_status_scan`
   - `indexing_tasks_scan`
4. 为 Day 8 的 `document.indexing_task.retry` 确认执行做准备。
