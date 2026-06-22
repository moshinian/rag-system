# Day 8：document.indexing_task.retry 确认执行计划

## 目标

Day 8 的目标是实现第一个 human-in-the-loop 写操作：

```text
用户确认 document.indexing_task.retry action
  -> Java 校验 run/action 归属和状态
  -> Java 校验工具白名单、风险等级和确认要求
  -> Java 调用 DocumentIndexingService.retry(...)
  -> Java 写回 agent_action 执行结果
```

Day 8 只实现 `document.indexing_task.retry` 的确认执行。`embedding.rebuild.submit` 仍留到 Day 9。

## 当前输入

Day 5 已完成：

1. Java 调用 Python Runtime。
2. Java 持久化 Runtime 返回的 steps/actions。
3. Java 生成 `ACT-...`。
4. 有待确认 action 时，run 进入 `WAITING_CONFIRMATION`。

Day 6 已完成：

1. `indexing.tasks.scan` 能扫描 `FAILED` 索引任务。
2. Python Runtime 能返回 `document.indexing_task.retry` action 草案。
3. action payload 形如：

```json
{
  "kbCode": "day20-cn-kb",
  "taskId": 1001,
  "documentCode": "DOC-failed-demo"
}
```

Day 7 已完成：

1. `FAILED indexing task -> document.indexing_task.retry -> WAITING_CONFIRMATION` 测试固定。
2. 已确认 action 进入 `PENDING_CONFIRMATION`。

现有业务执行能力：

```text
DocumentIndexingService.retry(String kbCode, String documentCode, Long taskId, String operator)
```

该方法已经负责：

1. 校验知识库存在且启用。
2. 校验文档属于知识库。
3. 校验文档未禁用。
4. 校验 task 属于文档。
5. 校验 task 为 `FAILED`。
6. 校验没有同文档活跃索引任务。
7. 校验未超过最大重试次数。
8. 创建 retry task 并 dispatch。

Day 8 应复用它，不复制 retry 业务规则。

## 关键边界

Day 8 必须继续遵守：

1. 写操作必须 human-in-the-loop。
2. Python 不执行写操作。
3. Python 不参与确认后的执行。
4. Java 是 action 状态和执行结果的唯一权威。
5. 只能执行白名单 action。
6. `HIGH` 风险 action 禁止执行。
7. action 必须属于当前 run。
8. run 必须属于当前知识库。
9. Day 8 只执行 `document.indexing_task.retry`。
10. Day 8 不执行 `embedding.rebuild.submit`。
11. Day 8 不接前端，只补 API 和后端测试。

## API 设计

Day 8 实现两个接口：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject
```

### confirm request

建议请求体：

```json
{
  "operator": "tester"
}
```

如果不想新增请求体，也可以从 query param 接收：

```text
?operator=tester
```

更推荐新增 request DTO：

```text
AgentActionConfirmRequest
```

字段：

```text
operator: String, optional
```

### reject request

建议请求体：

```json
{
  "operator": "tester",
  "reason": "暂不重试"
}
```

建议新增：

```text
AgentActionRejectRequest
```

字段：

```text
operator: String, optional
reason: String, optional
```

Day 8 如果时间紧，可以先实现 confirm，reject 留到 Day 12 前端接入前。但主计划里 confirm/reject 都是公开 API，推荐 Day 8 一并补最小 reject。

## Service 设计

建议在 `AgentRunService` 新增：

```text
AgentRunResponse confirmAction(String kbCode, String runCode, String actionCode, AgentActionConfirmRequest request)
AgentRunResponse rejectAction(String kbCode, String runCode, String actionCode, AgentActionRejectRequest request)
```

也可以拆出：

```text
AgentActionExecutionService
```

Day 8 推荐先放在 `AgentRunService`，因为当前 Agent 运行状态、查询和 action response 都在这里，减少横向扩散。

## confirm 流程

### 1. 校验归属

必须校验：

1. `kbCode` 存在。
2. `runCode` 存在。
3. run 属于当前知识库。
4. `actionCode` 存在。
5. action 属于当前 run。

### 2. 校验状态

要求：

```text
agent_run.status == WAITING_CONFIRMATION
agent_action.status == PENDING_CONFIRMATION
agent_action.requires_confirmation == true
```

如果 action 已经 confirmed / executing / succeeded / failed / rejected，要拒绝重复确认。

### 3. 校验风险和白名单

Day 8 允许执行：

```text
toolName = document.indexing_task.retry
riskLevel = MEDIUM
requiresConfirmation = true
```

拒绝：

1. `riskLevel = HIGH`
2. 未知 toolName
3. Day 8 未实现的 toolName，例如 `embedding.rebuild.submit`

### 4. 更新 action 为 EXECUTING

执行前写入：

```text
status = EXECUTING
confirmedBy = operator
confirmedAt = now
```

然后调用业务服务。

### 5. 执行业务动作

解析 `actionPayload`：

```json
{
  "kbCode": "day20-cn-kb",
  "taskId": 1001,
  "documentCode": "DOC-failed-demo"
}
```

调用：

```text
DocumentIndexingService.retry(kbCode, documentCode, taskId, operator)
```

注意：

1. path 中的 `kbCode` 必须和 payload 中 `kbCode` 一致，或者以 path 为准并校验 payload 不冲突。
2. `taskId` 必须存在且可解析为 Long。
3. `documentCode` 必须非空。

### 6. 写回执行结果

成功：

```text
status = SUCCEEDED
executedAt = now
resultJson = DocumentIndexingTaskResponse JSON
errorMessage = null
```

失败：

```text
status = FAILED
executedAt = now
errorMessage = exception message
resultJson = null 或保留失败上下文
```

Day 8 不建议吞掉业务异常。API 可以返回失败后的 run 详情，或者抛出业务异常并保证 action 已写为 FAILED。为了前端后续体验，推荐返回最新 run 详情。

### 7. run 状态处理

Day 8 推荐最小处理：

1. confirm 后 action 成功：
   - 如果 run 下没有其他 `PENDING_CONFIRMATION / EXECUTING` action，run 可更新为 `SUCCEEDED`。
   - 如果还有待确认 action，run 保持 `WAITING_CONFIRMATION`。
2. confirm 后 action 失败：
   - run 可更新为 `FAILED`，或保持 `WAITING_CONFIRMATION` 让用户查看失败 action。

推荐 Day 8 选择：

```text
action SUCCEEDED -> run SUCCEEDED
action FAILED -> run FAILED
```

理由：

1. v1 每个演示场景当前只生成一个 action。
2. 状态更容易展示和测试。
3. 多 action 复杂状态可以后续再扩展。

## reject 流程

最小 reject：

1. 校验归属。
2. 校验 action 是 `PENDING_CONFIRMATION`。
3. 设置：

```text
status = REJECTED
confirmedBy = operator
confirmedAt = now
errorMessage = reason
```

4. 如果 run 下没有其他待确认 action：

```text
run.status = SUCCEEDED
run.finishedAt = now
```

这里用 `SUCCEEDED` 表示“诊断流程已完成，用户拒绝了推荐动作”。如果后续想区分 rejected terminal state，需要新增 run status，不建议 Day 8 扩表。

## 建议新增或修改文件

建议新增：

```text
rag-backend/src/main/java/com/example/rag/model/request/AgentActionConfirmRequest.java
rag-backend/src/main/java/com/example/rag/model/request/AgentActionRejectRequest.java
rag-backend/src/test/java/com/example/rag/service/AgentActionExecutionTest.java
```

建议修改：

```text
rag-backend/src/main/java/com/example/rag/controller/AgentController.java
rag-backend/src/main/java/com/example/rag/service/AgentRunService.java
rag-backend/src/test/java/com/example/rag/controller/AgentControllerTest.java
rag-backend/src/test/java/com/example/rag/service/AgentRunServiceTest.java
docs/work/rag-agent/current-status.md
docs/work/rag-agent/README.md
```

可选新增：

```text
rag-backend/src/main/java/com/example/rag/model/dto/DocumentIndexingTaskRetryActionPayload.java
```

如果不新增 DTO，也至少用 `ObjectMapper.readTree(...)` 做结构化解析，不要用字符串截取。

## 测试计划

### Service 测试

建议覆盖：

1. confirm `document.indexing_task.retry` 成功：
   - action 从 `PENDING_CONFIRMATION` 到 `EXECUTING` 再到 `SUCCEEDED`
   - 调用 `DocumentIndexingService.retry(...)`
   - `resultJson` 写入 retry response
   - run 更新为 `SUCCEEDED`
2. confirm 时 action 不属于 run：
   - 拒绝
3. confirm 时 run 不属于 kb：
   - 拒绝
4. confirm 已执行过的 action：
   - 拒绝重复执行
5. confirm `HIGH` 风险 action：
   - 拒绝执行
6. confirm 未实现 toolName：
   - 拒绝执行，例如 `embedding.rebuild.submit`
7. retry 业务服务抛异常：
   - action 写为 `FAILED`
   - run 写为 `FAILED`
   - errorMessage 落库
8. reject pending action：
   - action 写为 `REJECTED`
   - run 写为 `SUCCEEDED`

### Controller 测试

建议覆盖：

1. `POST /confirm` 返回 run 详情。
2. `POST /reject` 返回 run 详情。
3. confirm 响应中 action status 为 `SUCCEEDED`。
4. reject 响应中 action status 为 `REJECTED`。

### 回归测试

继续跑：

1. `AgentRunScenarioTest`
2. `AgentRunServiceTest`
3. `AgentControllerTest`
4. Agent 工具测试

## 验证命令

Day 8 完成后建议执行：

```text
mvn -q -pl rag-backend -Dtest=AgentActionExecutionTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test
mvn -q -pl rag-backend -DskipTests compile
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
```

## 验收标准

Day 8 完成时应满足：

1. `POST /confirm` 可确认 `document.indexing_task.retry` action。
2. Java 会校验 run/action/kb 归属。
3. Java 会校验 action 状态为 `PENDING_CONFIRMATION`。
4. Java 会校验 toolName 白名单。
5. Java 会拒绝 `HIGH` 风险 action。
6. Java 会调用 `DocumentIndexingService.retry(...)`。
7. action 成功后进入 `SUCCEEDED`。
8. action 失败后进入 `FAILED` 并记录 errorMessage。
9. run 成功后进入 `SUCCEEDED`。
10. reject 可把 action 置为 `REJECTED`。
11. Day 8 不执行 `embedding.rebuild.submit`。
12. Day 8 不接前端。
13. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 8 暂不做：

1. `embedding.rebuild.submit` 确认执行。
2. 前端 Agent 工作台。
3. Python -> Java 真实工具 HTTP API。
4. 多 action 并发确认。
5. 批量 retry 多个 failed task。
6. `qa.retrieve.probe`。
7. LLM 润色报告。

## 下一步

Day 9 实现第二个确认执行动作：

```text
confirm embedding.rebuild.submit
  -> Java 校验 action 状态和白名单
  -> Java 提交知识库重嵌入后台任务
  -> agent_action 写入执行结果
```

Day 9 完成后，两个优先演示场景都具备 human-in-the-loop 运维闭环。

## Day 8 执行记录

实际完成内容：

1. 新增 `AgentActionConfirmRequest / AgentActionRejectRequest`。
2. 新增 confirm/reject API：
   - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm`
   - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject`
3. `AgentRunService` 已实现 `document.indexing_task.retry` 确认执行。
4. confirm 会校验：
   - knowledge base / run / action 归属
   - run 状态必须是 `WAITING_CONFIRMATION`
   - action 状态必须是 `PENDING_CONFIRMATION`
   - action 必须 `requiresConfirmation=true`
   - toolName 必须是 `document.indexing_task.retry`
   - riskLevel 不能是 `HIGH`
5. confirm 成功后由 Java 调用 `DocumentIndexingService.retry(...)`。
6. confirm 成功后 action 进入 `SUCCEEDED`，run 进入 `SUCCEEDED`。
7. confirm 业务失败后 action 进入 `FAILED`，run 进入 `FAILED`。
8. reject 后 action 进入 `REJECTED`，run 进入 `SUCCEEDED`。
9. `embedding.rebuild.submit` 仍明确不在 Day 8 执行，留到 Day 9。

新增或修改的关键文件：

1. `rag-backend/src/main/java/com/example/rag/model/request/AgentActionConfirmRequest.java`
2. `rag-backend/src/main/java/com/example/rag/model/request/AgentActionRejectRequest.java`
3. `rag-backend/src/main/java/com/example/rag/controller/AgentController.java`
4. `rag-backend/src/main/java/com/example/rag/service/AgentRunService.java`
5. `rag-backend/src/test/java/com/example/rag/service/AgentActionExecutionTest.java`
6. `rag-backend/src/test/java/com/example/rag/controller/AgentControllerTest.java`
7. `rag-backend/src/test/java/com/example/rag/service/AgentRunServiceTest.java`
8. `rag-backend/src/test/java/com/example/rag/service/AgentRunScenarioTest.java`
9. `docs/work/rag-agent/current-status.md`
10. `docs/work/rag-agent/README.md`

已验证：

```text
mvn -q -pl rag-backend -Dtest=AgentActionExecutionTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test
mvn -q -pl rag-backend -DskipTests compile
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
```
