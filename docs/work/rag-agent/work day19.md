# Day 19：安全拦截和 recommended action 落库边界

## 目标

Day 19 的目标是固定智能 Agent 的关键安全边界：

```text
Python Runtime 返回 recommendedActions
  -> Java 生成 actionCode
  -> agent_action.status = PENDING_CONFIRMATION
  -> agent_run.status = WAITING_CONFIRMATION
```

这一天不新增写工具执行能力，而是确认智能模式和 legacy 模式共享同一套 Java 权威落库、人审和执行边界。

## 当前输入

Day 18 已完成：

1. LLM 决策 JSON 校验。
2. toolName 白名单校验。
3. arguments schema 基础校验。
4. fake/mock LLM 失败恢复测试。
5. 工具循环上限保护。

现有 Java 能力：

1. `AgentRunService.createRun(...)`
2. `AgentRuntimeClient.run(...)`
3. `AgentRuntimeResponse.recommendedActions`
4. `AgentActionEntity`
5. `AgentRunStatus.WAITING_CONFIRMATION`
6. confirm/reject API。

## 边界约束

Day 19 必须遵守：

1. Python 不生成 `actionCode`。
2. Python 不直接写业务库。
3. Python 不直接执行写工具。
4. Java 根据 Runtime 返回的 action 草案统一落库。
5. 有 action 时 run 进入 `WAITING_CONFIRMATION`，不是 `SUCCEEDED`。
6. `LLM_DECISION` step 只是结构化决策审计，不是 chain-of-thought。

## 安全拦截规则

Runtime 根据 ToolDefinition 强制拦截：

```text
requiresConfirmation == true
or executionMode != READ_ONLY
or riskLevel in MEDIUM/HIGH
```

满足任一条件时：

1. 即使 planner 输出 `CALL_TOOL`，也不会执行工具。
2. 转为 Python `AgentActionDraft`。
3. Runtime response 返回 `recommendedActions`。
4. Java 持久化为 `PENDING_CONFIRMATION`。

## Java 落库规则

智能模式与 legacy 模式共享规则：

1. Java 创建 `agent_run`，状态先为 `RUNNING`。
2. Java 调 Python Runtime。
3. Java 持久化 Runtime 返回的 steps。
4. Java 持久化 Runtime 返回的 recommended actions。
5. 如果 recommended actions 非空：
   - `agent_run.status = WAITING_CONFIRMATION`
   - `agent_action.status = PENDING_CONFIRMATION`
   - `agent_run.finishedAt = null`
6. 如果无 action 且 Runtime 成功：
   - `agent_run.status = SUCCEEDED`
   - `finishedAt` 非空
7. 如果 Runtime 失败：
   - `agent_run.status = FAILED`
   - `errorMessage` 非空

## 实施内容

文件：

```text
rag-backend/src/test/java/com/example/rag/service/AgentRunServiceTest.java
rag-backend/src/test/java/com/example/rag/service/AgentRunScenarioTest.java
```

已完成：

1. 新增智能模式专项测试：
   - 请求 `runMode = INTELLIGENT_TOOL_AGENT`。
   - Runtime 返回 `LLM_DECISION` step。
   - Runtime 返回 `embedding.rebuild.submit` recommended action。
   - Java 生成 `AST-*` 和 `ACT-*`。
   - run 进入 `WAITING_CONFIRMATION`。
   - action 进入 `PENDING_CONFIRMATION`。
2. 验证 `AgentRuntimeRequest.runMode == INTELLIGENT_TOOL_AGENT` 会传给 Python Runtime。
3. 验证 `LLM_DECISION` step 可由 Java 落库和响应返回。

## 当前完成度核对

已完成：

1. 智能模式 recommended action 的 Java 落库测试。
2. 智能模式 `WAITING_CONFIRMATION` 状态测试。
3. `LLM_DECISION` step 持久化边界测试。

尚未完成：

1. 前端 runMode 选择项展示 `INTELLIGENT_TOOL_AGENT`。
2. 真实前后端智能模式联调。
3. 真实 LLM planner 接入后的端到端验收。
4. MCP/CLI 配置化。

## 验收标准

Day 19 完成时应满足：

1. 智能模式有 recommended action 时，Java 返回 `WAITING_CONFIRMATION`。
2. action 由 Java 生成 `actionCode`。
3. action 状态为 `PENDING_CONFIRMATION`。
4. `LLM_DECISION` step 能进入 Java response。
5. legacy 场景不受影响。

## 已验证

```text
mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -Dtest=AgentInternalToolControllerTest,AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest,QaRetrieveProbeAgentToolTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -DskipTests compile
```

## 下一步

Day 20 进入 MCP/CLI 最小接入：

1. 把 fake MCP tool 从静态定义推进到配置化最小 tool source。
2. 把只读 CLI tool 从静态分支推进到白名单模板 adapter。
3. 明确 CLI 不是通用 shell agent。
