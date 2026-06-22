# Day 6：documents/status 与 indexing/tasks 扫描

## 目标

Day 6 的目标是补齐 Agent v1 的 P1 只读诊断输入：

1. `documents.status.scan`
2. `indexing.tasks.scan`

这一天的重点是让 Agent 能看见知识库内文档状态和索引任务状态，为第二个演示场景提供依据：

```text
发现 FAILED indexing task
  -> 诊断索引异常
  -> 推荐 document.indexing_task.retry
  -> Java run 进入 WAITING_CONFIRMATION
```

Day 6 已完成只读扫描和推荐动作草案，不执行失败任务重试。

## 当前输入

Day 3 已完成 Java 工具边界：

1. `AgentTool`
2. `AgentToolRegistry`
3. `AgentToolContext`
4. `AgentToolResult`
5. `system.health.check`
6. `kb.readiness.check`

Day 4 已完成 Python LangGraph 最小图：

```text
parse_goal
  -> system_health_check
  -> kb_readiness_check
  -> diagnose
  -> recommend_actions
  -> generate_report
```

Day 5 已完成 Java 调用 Python Runtime 并落库：

1. Java 创建 run 后调用 Python `POST /v1/agent/runs`。
2. Java 持久化 Runtime 返回的 steps/actions。
3. Java 统一生成 `AST- / ACT-`。
4. Java 根据 action 决定 `WAITING_CONFIRMATION / SUCCEEDED / FAILED`。

现有后端可复用能力：

1. `DocumentRepository.findByKnowledgeBaseId(knowledgeBaseId)`
2. `IndexingTaskRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)`
3. `IndexingTaskRepository.findByDocumentIdAndTaskTypeOrderByCreatedAtDesc(documentId, taskType)`
4. `IndexingTaskStatus.FAILED`
5. `DocumentIndexingService.retry(...)` 已存在，但 Day 6 不调用。

## 关键边界

Day 6 必须继续遵守：

1. Java 是真实业务数据和工具白名单的权威。
2. Day 6 新增工具都是 `READ_ONLY`。
3. Day 6 不执行 `document.indexing_task.retry`。
4. Day 6 不实现 confirm/reject。
5. Python 仍不生成 `runCode / stepCode / actionCode`。
6. Python 可以根据扫描结果推荐 action 草案，但不能执行写操作。
7. Java 继续负责 run 状态、step/action 编码和落库。
8. Day 6 不做前端 Agent 工作台。
9. Day 6 不做 `qa.retrieve.probe`。

## 目标状态图

Day 6 后 Python LangGraph 已从 Day 4 最小图扩展为：

```text
parse_goal
  -> system_health_check
  -> kb_readiness_check
  -> documents_status_scan
  -> indexing_tasks_scan
  -> diagnose
  -> recommend_actions
  -> generate_report
```

暂不做条件分支。即使目标是 readiness 诊断，也可以顺手扫描文档和任务状态，保证演示输出稳定。

## Java 工具设计

### documents.status.scan

职责：

1. 读取当前知识库下文档列表。
2. 按文档状态聚合计数。
3. 返回少量异常样本，避免输出过大。
4. 不修改文档状态。

建议复用：

```text
KnowledgeBaseRepository.findByCode(kbCode)
DocumentRepository.findByKnowledgeBaseId(knowledgeBaseId)
```

建议输出 JSON：

```json
{
  "kbCode": "day20-cn-kb",
  "totalDocumentCount": 3,
  "statusCounts": {
    "UPLOADED": 0,
    "PROCESSING": 0,
    "INDEXED": 2,
    "FAILED": 1,
    "DISABLED": 0
  },
  "failedDocuments": [
    {
      "documentCode": "DOC-xxx",
      "documentName": "结算规则.pdf",
      "status": "FAILED",
      "errorMessage": "parse failed"
    }
  ]
}
```

字段以现有 `DocumentEntity` 为准。没有的字段不要硬造。

实现建议：

```text
DocumentsStatusAgentTool
```

工具定义：

```text
toolName = documents.status.scan
executionMode = READ_ONLY
maxRiskLevel = LOW
```

### indexing.tasks.scan

职责：

1. 扫描当前知识库下索引任务。
2. 聚合任务状态计数。
3. 返回 `FAILED` task 样本。
4. 为后续 `document.indexing_task.retry` action 草案提供 `documentCode / taskId`。
5. 不重试任务。

已新增 repository 方法：

```text
IndexingTaskRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId, int limit)
```

或者更聚焦：

```text
IndexingTaskRepository.findByKnowledgeBaseIdAndStatusesOrderByCreatedAtDesc(Long knowledgeBaseId, List<IndexingTaskStatus> statuses, int limit)
```

Day 6 已实现第二个方法，便于限制输出规模：

```text
statuses = FAILED / QUEUED / RUNNING / SUCCEEDED
limit = 50
```

建议输出 JSON：

```json
{
  "kbCode": "day20-cn-kb",
  "scannedTaskCount": 10,
  "statusCounts": {
    "QUEUED": 0,
    "RUNNING": 0,
    "SUCCEEDED": 8,
    "FAILED": 2
  },
  "failedTasks": [
    {
      "taskId": 123,
      "documentId": 456,
      "documentCode": "DOC-xxx",
      "taskType": "DOCUMENT_INDEXING",
      "taskStage": "EMBEDDING",
      "retryCount": 1,
      "maxRetryCount": 3,
      "errorMessage": "Embedding provider failed"
    }
  ]
}
```

如果 `IndexingTaskEntity` 没有 `documentCode`，有两种选择：

1. 在工具里用 `DocumentRepository.findById(documentId)` 补齐。
2. Day 6 先只返回 `documentId / taskId`，Day 8 执行 retry 时再做严格归属校验。

更推荐 Day 6 补 `documentCode`，因为 action payload 和前端展示都更直观。

已实现：

```text
IndexingTasksScanAgentTool
```

工具定义：

```text
toolName = indexing.tasks.scan
executionMode = READ_ONLY
maxRiskLevel = LOW
```

## Python Runtime 改造

### Tool Client

Day 4 当前 `StaticAgentToolClient` 仍是替身。Day 6 选择了先扩展静态工具样例：

1. 先扩展 `StaticAgentToolClient`，让它支持 `documents.status.scan` 和 `indexing.tasks.scan` 的受控样例输出。
2. 同时或随后实现 Python -> Java 真实工具 HTTP API。

选择这条路线的原因：

1. Day 5 已经验证 Java -> Python -> Java 落库链路。
2. Day 6 的目标是补图和诊断规则，不是解决 Python -> Java 工具 HTTP。
3. 真实工具 HTTP API 可以单独作为 Day 6.5 或 Day 7 前置收口。

但文档和代码必须明确：

```text
StaticAgentToolClient remains a Day 4/Day 6 runtime stub.
Java tools are the authoritative implementation.
```

不能把静态样例当作真实业务扫描。

### graph.py

已新增节点：

```text
documents_status_scan
indexing_tasks_scan
```

节点职责：

1. 调用对应 toolName。
2. 记录 TOOL_CALL step。
3. 写入 `tool_results`。

图顺序：

```text
kb_readiness_check -> documents_status_scan -> indexing_tasks_scan -> diagnose
```

### diagnose

Day 6 诊断规则已新增：

1. 如果 `indexing.tasks.scan.failedTasks` 非空：
   - `primaryCause = FAILED_INDEXING_TASK`
2. 否则沿用 Day 4：
   - `REEMBED_REQUIRED`
   - `SYSTEM_HEALTH_UNAVAILABLE`
   - `NO_BLOCKING_ISSUE_FOUND`

优先级建议：

```text
SYSTEM_HEALTH_UNAVAILABLE
FAILED_INDEXING_TASK
REEMBED_REQUIRED
NO_BLOCKING_ISSUE_FOUND
```

原因：

1. 系统基础依赖异常优先级最高。
2. FAILED indexing task 是 Day 6 主场景。
3. reembedRequired 是 Day 7 的完整场景延续。

### recommend_actions

Day 6 已新增动作草案：

```text
toolName = document.indexing_task.retry
title = 重试失败索引任务
riskLevel = MEDIUM
requiresConfirmation = true
```

触发条件：

```text
indexing.tasks.scan.failedTasks 非空
```

建议 `actionPayload`：

```json
{
  "kbCode": "day20-cn-kb",
  "taskId": 123,
  "documentCode": "DOC-xxx"
}
```

如果有多个失败任务，Day 6 先推荐第一个最明确的失败任务即可，不做批量 action。

Day 6 不实现 `document.indexing_task.retry` 真实执行。Day 8 再做确认执行。

## 建议新增或修改文件

后端已新增：

```text
rag-backend/src/main/java/com/example/rag/service/agent/DocumentsStatusAgentTool.java
rag-backend/src/main/java/com/example/rag/service/agent/IndexingTasksScanAgentTool.java
rag-backend/src/test/java/com/example/rag/service/agent/DocumentsStatusAgentToolTest.java
rag-backend/src/test/java/com/example/rag/service/agent/IndexingTasksScanAgentToolTest.java
```

后端已修改：

```text
rag-backend/src/main/java/com/example/rag/persistence/IndexingTaskRepository.java
```

Python 已修改：

```text
rag-ai-service/app/agent/tools.py
rag-ai-service/app/agent/graph.py
rag-ai-service/tests/test_agent_runtime.py
```

文档已修改：

```text
docs/work/rag-agent/current-status.md
docs/work/rag-agent/README.md
```

## 已验证

### Java 工具测试

`DocumentsStatusAgentToolTest` 已覆盖：

1. 调用 `DocumentRepository.findByKnowledgeBaseId(...)`。
2. 输出 `totalDocumentCount`。
3. 输出 `statusCounts`。
4. 存在失败文档时输出 `failedDocuments`。
5. 工具定义为：
   - `executionMode = READ_ONLY`
   - `maxRiskLevel = LOW`

`IndexingTasksScanAgentToolTest` 已覆盖：

1. 调用 `IndexingTaskRepository` 扫描当前知识库任务。
2. 输出 `statusCounts`。
3. 存在 `FAILED` 任务时输出 `failedTasks`。
4. 失败任务包含 `taskId / documentCode / retryCount / errorMessage`。
5. 工具定义为：
   - `executionMode = READ_ONLY`
   - `maxRiskLevel = LOW`

`AgentToolRegistryTest` 已覆盖：

1. 注册表包含：
   - `documents.status.scan`
   - `indexing.tasks.scan`

### Python Runtime 测试

`test_agent_runtime.py` 已增补：

1. 最小图节点顺序包含：
   - `documents_status_scan`
   - `indexing_tasks_scan`
2. `FAILED indexing task` 样例会生成：
   - `document.indexing_task.retry`
3. action 不包含 `actionCode`。
4. `DIAGNOSE_ONLY` 下不返回 retry action。

### Java Runtime 落库测试

`AgentRunServiceTest` 已增加：

1. Runtime 返回 `document.indexing_task.retry` action 草案时：
   - Java 生成 `ACT-`
   - action status = `PENDING_CONFIRMATION`
   - run status = `WAITING_CONFIRMATION`

这项也可以留到 Day 7 场景验收时补，但 Day 6 补掉更稳。

## 验证命令

Day 6 已执行：

```text
mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test
mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest,AgentRuntimeClientTest test
mvn -q -pl rag-backend -DskipTests compile
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
./venv/bin/python -m py_compile rag-ai-service/app/agent/state.py rag-ai-service/app/agent/tools.py rag-ai-service/app/agent/graph.py rag-ai-service/app/agent/runtime.py rag-ai-service/app/api/routes.py
```

## 验收结果

Day 6 已满足：

1. Java 注册表包含 `documents.status.scan`。
2. Java 注册表包含 `indexing.tasks.scan`。
3. 两个新工具都是 `READ_ONLY / LOW`。
4. 文档扫描能返回状态聚合和失败文档样本。
5. 索引任务扫描能返回状态聚合和失败任务样本。
6. Python LangGraph 执行轨迹包含两个新节点。
7. 存在 FAILED indexing task 时，Python 返回 `document.indexing_task.retry` action 草案。
8. Java 仍统一生成 `stepCode/actionCode`。
9. 有 retry action 草案时，run 进入 `WAITING_CONFIRMATION`。
10. Day 6 不执行任何写操作。
11. Day 6 不实现 confirm/reject。
12. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 6 暂不做：

1. `document.indexing_task.retry` 真实执行。
2. `embedding.rebuild.submit` 真实执行。
3. confirm/reject。
4. 前端 Agent 工作台。
5. `qa.retrieve.probe`。
6. LLM 润色报告。
7. 多失败任务批量 retry action。

## 下一步

Day 7 进入第一个完整诊断场景验收：

```text
readiness 异常
  -> reembedRequired=true
  -> 推荐 embedding.rebuild.submit
  -> Java 落库 action
  -> run.status = WAITING_CONFIRMATION
```

如果 Day 6 同时完成 FAILED indexing task 的 Runtime 推荐链路，Day 7 可以顺手补第二个演示样例的端到端 smoke test，但真实 retry 执行仍留到 Day 8。
