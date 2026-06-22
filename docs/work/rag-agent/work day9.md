# Day 9：embedding.rebuild.submit 确认执行计划

## 目标

Day 9 的目标是实现第二个 human-in-the-loop 写操作：

```text
用户确认 embedding.rebuild.submit action
  -> Java 校验 run/action 归属和状态
  -> Java 校验工具白名单、风险等级和确认要求
  -> Java 调用 EmbeddingRebuildService.submit(...)
  -> Java 写回 agent_action 执行结果
```

Day 9 完成后，两个优先演示场景都具备“诊断 -> 推荐 -> 人工确认 -> Java 执行 -> 审计落库”的闭环：

1. `reembedRequired -> embedding.rebuild.submit -> confirm -> SUCCEEDED/FAILED`
2. `FAILED indexing task -> document.indexing_task.retry -> confirm -> SUCCEEDED/FAILED`

## 当前输入

Day 4 已完成：

1. Python Runtime 能在 `reembedRequired=true` 时返回 `embedding.rebuild.submit` action 草案。
2. action payload 形如：

```json
{
  "kbCode": "day20-cn-kb"
}
```

Day 5 已完成：

1. Java 调用 Python Runtime。
2. Java 持久化 steps/actions。
3. Java 生成 `ACT-...`。
4. 有待确认 action 时，run 进入 `WAITING_CONFIRMATION`。

Day 7 已完成：

1. `reembedRequired -> embedding.rebuild.submit -> WAITING_CONFIRMATION` 场景测试固定。
2. action 已进入 `PENDING_CONFIRMATION`。

Day 8 已完成：

1. 已有 confirm/reject API。
2. 已有 run/action/kb 归属校验。
3. 已有 action 状态校验。
4. 已有 `HIGH` risk 拒绝策略。
5. 已有 action 执行结果写回模式。
6. 已有 `document.indexing_task.retry` 的第一个确认执行闭环。

现有业务执行能力：

```text
EmbeddingRebuildService.submit(String operator)
```

该方法已经负责：

1. 校验当前没有活跃 embedding rebuild run。
2. 校验当前没有活跃文档索引任务。
3. 校验 embedding 配置确实发生变化。
4. 查询所有 active knowledge base 下的 indexed documents。
5. 创建 `embedding_rebuild_run`。
6. 重置旧 embedding 状态。
7. 标记 rebuild submitted。
8. 事务提交后异步 dispatch rebuild run。
9. 返回 `EmbeddingRebuildSubmitResponse`。

Day 9 应复用它，不复制重嵌入业务规则。

## 关键边界

Day 9 必须继续遵守：

1. 写操作必须 human-in-the-loop。
2. Python 不执行写操作。
3. Python 不参与确认后的执行。
4. Java 是 action 状态和执行结果的唯一权威。
5. Java 仍统一生成 `runCode / stepCode / actionCode`。
6. 只能执行白名单 action。
7. `HIGH` 风险 action 禁止执行。
8. action 必须属于当前 run。
9. run 必须属于当前知识库。
10. Day 9 不接前端，只补后端执行能力和测试。

## 重要设计取舍

当前 `EmbeddingRebuildService.submit(String operator)` 是“全量重嵌入”入口，不接收 `kbCode` 参数。

所以 Day 9 不应把它伪装成“单知识库重嵌入”。推荐处理方式：

1. path 中的 `kbCode` 仍用于校验 run/action 归属。
2. action payload 中的 `kbCode` 必须和 path `kbCode` 一致，避免 action 被跨知识库确认。
3. 真正执行业务时调用现有全量方法：

```text
EmbeddingRebuildService.submit(operator)
```

4. action title/reason/summary 中可继续表达“当前知识库 readiness 触发了重嵌入建议”。
5. `resultJson` 写入 `EmbeddingRebuildSubmitResponse`，其中包含真实 rebuild run 信息。

如果后续要支持单知识库重嵌入，应单独扩展业务服务，不在 Day 9 临时改造。

## API 设计

Day 9 继续复用 Day 8 已有接口：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject
```

confirm request 继续使用：

```json
{
  "operator": "tester"
}
```

reject request 继续使用：

```json
{
  "operator": "tester",
  "reason": "暂不重嵌入"
}
```

Day 9 不新增公开 API。

## Service 设计

继续在 `AgentRunService` 内扩展 Day 8 的执行分发逻辑。

Day 8 当前只允许：

```text
document.indexing_task.retry
```

Day 9 扩展为允许：

```text
document.indexing_task.retry
embedding.rebuild.submit
```

建议把执行逻辑拆成工具分发：

```text
executeConfirmedAction(kbCode, action, operator)
  switch action.toolName:
    document.indexing_task.retry -> executeRetryIndexingTask(...)
    embedding.rebuild.submit -> executeEmbeddingRebuildSubmit(...)
```

这样 Day 10 以后不会继续把 confirm 主流程写成一串 if/else。

## confirm 流程

### 1. 复用 Day 8 通用校验

必须继续校验：

```text
agent_run.status == WAITING_CONFIRMATION
agent_action.status == PENDING_CONFIRMATION
agent_action.requires_confirmation == true
```

并继续校验：

1. `kbCode` 存在。
2. `runCode` 存在。
3. run 属于当前知识库。
4. `actionCode` 存在。
5. action 属于当前 run。

### 2. 扩展白名单

Day 9 允许执行：

```text
toolName = embedding.rebuild.submit
riskLevel = MEDIUM
requiresConfirmation = true
```

继续拒绝：

1. `riskLevel = HIGH`
2. 未知 toolName
3. `requiresConfirmation=false` 的写操作

### 3. 更新 action 为 EXECUTING

执行前写入：

```text
status = EXECUTING
confirmedBy = operator
confirmedAt = now
errorMessage = null
```

### 4. 解析 action payload

解析 payload：

```json
{
  "kbCode": "day20-cn-kb"
}
```

校验：

1. payload 必须是合法 JSON。
2. `kbCode` 可为空；如果存在，必须和 path `kbCode` 一致。
3. Day 9 不从 payload 读取 rebuildRunId，也不允许 Python 指定业务 run id。

### 5. 执行业务动作

调用：

```text
EmbeddingRebuildService.submit(operator)
```

注意：

1. 该方法会自行判断是否有活跃 rebuild run。
2. 该方法会自行判断是否有活跃 indexing task。
3. 该方法会自行判断 embedding 配置是否确实变化。
4. Day 9 不复制这些规则。

### 6. 写回执行结果

成功：

```text
status = SUCCEEDED
executedAt = now
resultJson = EmbeddingRebuildSubmitResponse JSON
errorMessage = null
run.status = SUCCEEDED
run.finishedAt = now
```

失败：

```text
status = FAILED
executedAt = now
resultJson = null
errorMessage = exception message
run.status = FAILED
run.errorMessage = exception message
run.finishedAt = now
```

Day 9 继续沿用 Day 8 策略：API 返回最新 run 详情，不把业务失败吞成无状态错误。

## reject 流程

Day 9 不需要新增 reject 逻辑。继续复用 Day 8：

1. action 必须是 `PENDING_CONFIRMATION`。
2. action 写为 `REJECTED`。
3. `confirmedBy / confirmedAt / errorMessage(reason)` 写回。
4. run 写为 `SUCCEEDED`。

## 建议新增或修改文件

建议修改：

```text
rag-backend/src/main/java/com/example/rag/service/AgentRunService.java
rag-backend/src/test/java/com/example/rag/service/AgentActionExecutionTest.java
rag-backend/src/test/java/com/example/rag/service/AgentRunScenarioTest.java
docs/work/rag-agent/current-status.md
docs/work/rag-agent/README.md
```

可能需要修改：

```text
rag-backend/src/test/java/com/example/rag/controller/AgentControllerTest.java
```

不建议新增公开 request DTO。Day 8 已经有：

```text
AgentActionConfirmRequest
AgentActionRejectRequest
```

可选新增私有 payload record：

```text
EmbeddingRebuildSubmitActionPayload
```

如果不新增 record，也至少用 `ObjectMapper.readTree(...)` 做结构化解析，不要用字符串截取。

## 测试计划

### Service 测试

建议在 `AgentActionExecutionTest` 增加：

1. confirm `embedding.rebuild.submit` 成功：
   - action 从 `PENDING_CONFIRMATION` 到 `EXECUTING` 再到 `SUCCEEDED`
   - 调用 `EmbeddingRebuildService.submit(operator)`
   - `resultJson` 写入 `EmbeddingRebuildSubmitResponse`
   - run 更新为 `SUCCEEDED`
2. payload `kbCode` 和 path `kbCode` 不一致：
   - 拒绝执行
   - 不调用 `EmbeddingRebuildService.submit(...)`
3. `EmbeddingRebuildService.submit(...)` 抛业务异常：
   - action 写为 `FAILED`
   - run 写为 `FAILED`
   - errorMessage 落库
4. `HIGH` risk 的 `embedding.rebuild.submit`：
   - 拒绝执行
5. 未知 toolName：
   - 拒绝执行
6. 已执行过的 action：
   - 拒绝重复确认
7. `requiresConfirmation=false`：
   - 拒绝执行

### 场景测试

建议扩展 `AgentRunScenarioTest`：

1. readiness 异常场景继续固定：
   - `embedding.rebuild.submit`
   - `PENDING_CONFIRMATION`
   - `WAITING_CONFIRMATION`
2. 新增 confirm 后闭环：
   - confirm `embedding.rebuild.submit`
   - run 进入 `SUCCEEDED`
   - action 进入 `SUCCEEDED`
   - `resultJson` 包含 `rebuildRunId/status/targetFingerprint`

### Controller 测试

现有 confirm endpoint 已覆盖通用返回形状。Day 9 可只补 service 测试。

如果要更完整，可以补：

1. confirm `embedding.rebuild.submit` 返回 action status `SUCCEEDED`。
2. 失败时通过全局异常或 run response 展示错误。

### 回归测试

继续跑：

1. `AgentActionExecutionTest`
2. `AgentRunServiceTest`
3. `AgentControllerTest`
4. `AgentRunScenarioTest`
5. Agent 工具测试
6. Python Runtime 测试

## 验证命令

Day 9 完成后建议执行：

```text
mvn -q -pl rag-backend -Dtest=AgentActionExecutionTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -Dtest=EmbeddingRebuildServiceTest,AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test
mvn -q -pl rag-backend -DskipTests compile
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
```

## 验收标准

Day 9 完成时应满足：

1. `POST /confirm` 可确认 `embedding.rebuild.submit` action。
2. Java 会校验 run/action/kb 归属。
3. Java 会校验 action 状态为 `PENDING_CONFIRMATION`。
4. Java 会校验 action `requiresConfirmation=true`。
5. Java 会校验 toolName 白名单。
6. Java 会拒绝 `HIGH` 风险 action。
7. Java 会校验 payload `kbCode` 不和 path `kbCode` 冲突。
8. Java 会调用 `EmbeddingRebuildService.submit(...)`。
9. action 成功后进入 `SUCCEEDED`。
10. action 失败后进入 `FAILED` 并记录 errorMessage。
11. run 成功后进入 `SUCCEEDED`。
12. run 失败后进入 `FAILED`。
13. Python 不执行重嵌入。
14. Python 不生成 rebuild run id。
15. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 9 暂不做：

1. 单知识库级别 embedding rebuild。
2. 前端 Agent 工作台。
3. Python -> Java 真实工具 HTTP API。
4. 多 action 并发确认。
5. `qa.retrieve.probe`。
6. LLM 润色报告。
7. 重嵌入进度 timeline 展示。

## 下一步

Day 10 开始前端 Agent 工作台最小闭环：

```text
创建 run
  -> 查询 run
  -> 展示 summary/status
  -> 展示 steps/actions 基础信息
```

Day 10 不需要立刻做复杂 timeline 和 confirm/reject UI；先把 Agent 运行结果可视化出来。

## Day 9 执行记录

实际完成内容：

1. `AgentRunService` 已从单一 retry 执行扩展为确认后工具分发。
2. confirm 白名单支持：
   - `document.indexing_task.retry`
   - `embedding.rebuild.submit`
3. `embedding.rebuild.submit` 已接入：
   - `EmbeddingRebuildService.submit(operator)`
4. 已保留现有业务语义：
   - `EmbeddingRebuildService.submit(...)` 是全量重嵌入
   - Day 9 不新增单知识库重嵌入
   - payload `kbCode` 只做防串库校验
5. confirm `embedding.rebuild.submit` 成功后：
   - action 写为 `SUCCEEDED`
   - `resultJson` 写入 `EmbeddingRebuildSubmitResponse`
   - run 写为 `SUCCEEDED`
6. confirm `embedding.rebuild.submit` 失败后：
   - action 写为 `FAILED`
   - run 写为 `FAILED`
   - errorMessage 落库
7. 继续拒绝 `HIGH` risk action 和未知 toolName。

新增或修改的关键文件：

1. `rag-backend/src/main/java/com/example/rag/service/AgentRunService.java`
2. `rag-backend/src/test/java/com/example/rag/service/AgentActionExecutionTest.java`
3. `rag-backend/src/test/java/com/example/rag/service/AgentRunServiceTest.java`
4. `rag-backend/src/test/java/com/example/rag/service/AgentRunScenarioTest.java`
5. `docs/work/rag-agent/current-status.md`
6. `docs/work/rag-agent/README.md`
7. `docs/work/rag-agent/work day9.md`

已验证：

```text
mvn -q -pl rag-backend -Dtest=AgentActionExecutionTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -Dtest=EmbeddingRebuildServiceTest,AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
mvn -q -pl rag-backend -DskipTests compile
```
