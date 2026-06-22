# Day 7：演示场景端到端验收

## 目标

Day 7 的目标是对前 6 天已经完成的 Agent 主链路做端到端场景验收，重点证明 Java / Python / 持久化三层已经能稳定跑通：

```text
创建 Agent run
  -> Python LangGraph Runtime 执行诊断图
  -> Python 返回 steps/actions 草案
  -> Java 生成 stepCode/actionCode
  -> Java 落库 steps/actions
  -> run 进入 WAITING_CONFIRMATION
```

Day 7 未新增写操作执行能力，不做 confirm/reject。它的定位是把“诊断和推荐”主链路跑稳，为 Day 8/Day 9 的确认执行做准备。

本日已验收两个演示场景：

1. `reembedRequired -> embedding.rebuild.submit -> WAITING_CONFIRMATION`
2. `FAILED indexing task -> document.indexing_task.retry -> WAITING_CONFIRMATION`

## 当前输入

Day 4 已完成 Python Runtime：

1. `POST /v1/agent/runs`
2. LangGraph 节点：
   - `parse_goal`
   - `system_health_check`
   - `kb_readiness_check`
   - `documents_status_scan`
   - `indexing_tasks_scan`
   - `diagnose`
   - `recommend_actions`
   - `generate_report`
3. Runtime 返回：
   - `status`
   - `summary`
   - `steps`
   - `recommendedActions`

Day 5 已完成 Java 调用和落库：

1. Java 创建 `agent_run`。
2. Java 调用 Python Runtime。
3. Java 持久化 Runtime 返回的 steps/actions。
4. Java 统一生成：
   - `runCode = AR-...`
   - `stepCode = AST-...`
   - `actionCode = ACT-...`
5. 有待确认 action 时，run 进入 `WAITING_CONFIRMATION`。

Day 6 已完成 P1 只读扫描：

1. Java 工具：
   - `documents.status.scan`
   - `indexing.tasks.scan`
2. Python 节点：
   - `documents_status_scan`
   - `indexing_tasks_scan`
3. 诊断规则：
   - `FAILED_INDEXING_TASK`
4. 推荐动作草案：
   - `document.indexing_task.retry`

## 关键边界

Day 7 必须继续遵守：

1. Java 是 Agent Run 状态中心。
2. Java 统一生成 `stepCode / actionCode`。
3. Python 不生成业务编码。
4. Python 不写业务库。
5. Python 不执行写操作。
6. Day 7 不执行 `embedding.rebuild.submit`。
7. Day 7 不执行 `document.indexing_task.retry`。
8. Day 7 不实现 confirm/reject。
9. Day 7 不接前端。
10. Day 7 只做场景验收和必要的测试补强。

## 场景 1：reembedRequired

### 输入

```json
{
  "goal": "诊断这个知识库为什么不能问答",
  "runMode": "DIAGNOSE_AND_RECOMMEND"
}
```

当前 Day 4/Day 6 的 `StaticAgentToolClient` 会根据 “不能问答 / readiness / reembed / 重嵌入” 等关键词返回：

```text
kb.readiness.check.reembedRequired = true
```

### 预期 Runtime 诊断

Python Runtime 预期：

```text
primaryCause = REEMBED_REQUIRED
summary = 知识库当前不可问答，主要原因是 embedding 配置变化后尚未完成重嵌入。
recommendedActions[0].toolName = embedding.rebuild.submit
recommendedActions[0].requiresConfirmation = true
recommendedActions[0].riskLevel = MEDIUM
```

### 预期 Java run

Java 预期：

```text
agent_run.status = WAITING_CONFIRMATION
agent_run.summary 非空
agent_step 至少包含完整 LangGraph 节点轨迹
agent_action[0].tool_name = embedding.rebuild.submit
agent_action[0].status = PENDING_CONFIRMATION
agent_action[0].action_code = ACT-...
```

### 验收重点

1. Runtime response 不包含 `actionCode`。
2. Java response 包含 `actionCode`。
3. Java run 进入 `WAITING_CONFIRMATION`。
4. 不触发真实重嵌入。

## 场景 2：FAILED indexing task

### 输入

```json
{
  "goal": "检查这个知识库有没有索引异常",
  "runMode": "DIAGNOSE_AND_RECOMMEND"
}
```

当前 Day 6 的 `StaticAgentToolClient` 会根据 “索引异常 / 索引失败 / failed indexing / failed task” 等关键词返回：

```text
indexing.tasks.scan.failedTasks 非空
```

### 预期 Runtime 诊断

Python Runtime 预期：

```text
primaryCause = FAILED_INDEXING_TASK
summary = 知识库存在失败的索引任务，需要人工确认后重试失败任务。
recommendedActions[0].toolName = document.indexing_task.retry
recommendedActions[0].requiresConfirmation = true
recommendedActions[0].riskLevel = MEDIUM
recommendedActions[0].actionPayload 包含 taskId/documentCode
```

### 预期 Java run

Java 预期：

```text
agent_run.status = WAITING_CONFIRMATION
agent_action[0].tool_name = document.indexing_task.retry
agent_action[0].status = PENDING_CONFIRMATION
agent_action[0].action_payload 包含 taskId/documentCode
```

### 验收重点

1. `indexing_tasks_scan` step 出现在 timeline 中。
2. `document.indexing_task.retry` action 被 Java 落库。
3. Java 生成 `ACT-...`。
4. 不执行真实 retry。

## 已新增或修改文件

后端已新增：

```text
rag-backend/src/test/java/com/example/rag/service/AgentRunScenarioTest.java
```

Python 已扩展：

```text
rag-ai-service/tests/test_agent_runtime.py
```

文档已修改：

```text
docs/work/rag-agent/current-status.md
docs/work/rag-agent/README.md
```

Day 7 只补测试和文档，不新增生产代码。核心是把两个演示场景用测试固定下来。

## 已验证

### Python Runtime

已补充或确认：

1. readiness 异常输入返回 `embedding.rebuild.submit`。
2. 索引异常输入返回 `document.indexing_task.retry`。
3. 两个场景都不返回 `actionCode`。
4. `DIAGNOSE_ONLY` 下不返回写操作 action。
5. steps 节点顺序完整：
   - `parse_goal`
   - `system_health_check`
   - `kb_readiness_check`
   - `documents_status_scan`
   - `indexing_tasks_scan`
   - `diagnose`
   - `recommend_actions`
   - `generate_report`

### Java Service

已补充或确认：

1. Runtime 返回 `embedding.rebuild.submit` 时：
   - Java 生成 `ACT-`
   - action status = `PENDING_CONFIRMATION`
   - run status = `WAITING_CONFIRMATION`
2. Runtime 返回 `document.indexing_task.retry` 时：
   - Java 生成 `ACT-`
   - action status = `PENDING_CONFIRMATION`
   - action payload 保留 `taskId/documentCode`
   - run status = `WAITING_CONFIRMATION`
3. Runtime 返回 steps 时：
   - Java 生成 `AST-`
   - steps 按返回顺序落库。

### API 层

已补充或确认：

1. `POST /api/knowledge-bases/{kbCode}/agent/runs` 返回 `202 Accepted`。
2. 响应体里 `status = WAITING_CONFIRMATION`。
3. 响应体里包含：
   - `summary`
   - `steps`
   - `actions`
4. action 有 Java 生成的 `actionCode`。

### 可选本地联调

Day 7 未执行本地真实服务 smoke。当前 Python Runtime 仍使用 `StaticAgentToolClient`，不能把 smoke 结果宣称为真实 Java 工具扫描闭环。

后续如果本地服务可启动，可以做一个 smoke：

1. 启动 `rag-ai-service`。
2. 启动 `rag-backend`。
3. 调用：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs
```

4. 再调用：

```text
GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}
```

5. 确认 run 详情里有 summary、steps、actions。

## 验证命令

Day 7 已执行：

```text
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest,AgentRuntimeClientTest test
mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test
mvn -q -pl rag-backend -DskipTests compile
```

如新增独立场景测试：

```text
mvn -q -pl rag-backend -Dtest=AgentRunScenarioTest test
```

## 验收结果

Day 7 已满足：

1. `reembedRequired` 场景有测试固定。
2. `FAILED indexing task` 场景有测试固定。
3. 两个场景都能返回 action 草案。
4. 两个场景落到 Java 后 run 都进入 `WAITING_CONFIRMATION`。
5. 两个场景 action 都是 `PENDING_CONFIRMATION`。
6. 两个场景 action 都有 Java 生成的 `ACT-...`。
7. Runtime response 仍不包含 `stepCode/actionCode`。
8. Day 7 不执行任何写操作。
9. Day 7 不实现 confirm/reject。
10. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 7 暂不做：

1. Python -> Java 真实工具 HTTP API。
2. `document.indexing_task.retry` 真实执行。
3. `embedding.rebuild.submit` 真实执行。
4. confirm/reject。
5. 前端 Agent 工作台。
6. `qa.retrieve.probe`。
7. LLM 润色报告。

## 下一步

Day 8 进入第一个确认执行动作：

```text
confirm document.indexing_task.retry
  -> Java 校验 action 状态和白名单
  -> Java 执行 DocumentIndexingService.retry(...)
  -> agent_action 写入执行结果
```

Day 8 的重点会从“诊断推荐”转到 “human-in-the-loop 后的受控写操作执行”。
