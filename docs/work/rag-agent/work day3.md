# Day 3：P0 只读工具封装

## 目标

Day 3 的目标是先在 Java 侧建立 Agent 可控工具边界，封装两个 P0 只读工具：

1. `system.health.check`
2. `kb.readiness.check`

Day 3 的重点不是 LangGraph，也不是 Python Runtime，而是把现有系统能力变成 Agent 可调用、可白名单管理、可序列化返回的工具契约。

本日完成：

1. 定义 Agent 工具执行结果结构。
2. 定义 Agent 工具接口或执行器抽象。
3. 建立 Agent 工具白名单注册表。
4. 封装 `system.health.check`。
5. 封装 `kb.readiness.check`。
6. 补充工具层单元测试。

Day 3 不调用 Python，不持久化 tool step，不修改 Agent run 状态，也不接前端。

## 当前输入

Day 1 已完成：

1. `agent_run / agent_step / agent_action` 三张表。
2. Agent 状态枚举。
3. Agent Runtime 协议 DTO。
4. Python `AgentState` 草案。

Day 2 已完成：

1. Agent run 创建与详情查询 API。
2. `AgentRunService`。
3. `AgentController`。
4. `AgentRunServiceTest / AgentControllerTest`。

Day 3 应直接复用现有服务：

1. `SystemHealthService.currentStatus()`
2. `QuestionAnsweringService.getReadiness(kbCode)`

不要重复实现健康检查或 readiness 逻辑。

## 已完成

### AgentToolResult

新增 DTO：

```text
toolName
success
outputJson
errorMessage
durationMs
```

设计意图：

1. 统一所有工具执行结果。
2. 后续 Day 5 可以直接转换为 `AgentRuntimeStepResult` 或 `agent_step`。
3. 工具失败时不直接吞异常，返回明确 errorMessage。

当前工具成功时返回 `AgentToolResult.success(...)`。序列化失败会抛出 `BusinessException`，后续 Day 5 落 step 时再统一映射失败轨迹。

### AgentTool

新增接口：

```text
String toolName();
AgentToolDefinition definition();
AgentToolResult execute(AgentToolContext context);
```

其中 `AgentToolDefinition` 已在 Day 1 新增，包含：

```text
toolName
executionMode
maxRiskLevel
```

### AgentToolContext

新增上下文对象：

```text
kbCode
question
runCode
operator
Map<String, Object> attributes
```

Day 3 实际只需要 `kbCode`。

保留 `question/runCode/operator/attributes` 是为了 Day 6/Day 13 承接：

1. `indexing.tasks.scan`
2. `qa.retrieve.probe`
3. 后续执行轨迹落库

### AgentToolRegistry

已实现职责：

1. 注册所有 Agent 工具。
2. 按 `toolName` 获取工具。
3. 暴露工具白名单列表。
4. 启动时拒绝重复 toolName。

Day 3 已注册：

```text
system.health.check
kb.readiness.check
```

工具定义：

```text
system.health.check
executionMode = READ_ONLY
maxRiskLevel = LOW

kb.readiness.check
executionMode = READ_ONLY
maxRiskLevel = LOW
```

### system.health.check

实现方式：

1. 调用 `SystemHealthService.currentStatus()`。
2. 将 `HealthStatusResponse` 序列化成 JSON。
3. 返回 `AgentToolResult`。

输出重点：

1. `status`
2. `serviceName`
3. `components`
4. `checkedAt`

边界：

1. 不执行 Redis probe。
2. 不主动消耗模型 token。
3. 不修改任何系统状态。

### kb.readiness.check

实现方式：

1. 调用 `QuestionAnsweringService.getReadiness(kbCode)`。
2. 将 `QuestionAnsweringReadinessResponse` 序列化成 JSON。
3. 返回 `AgentToolResult`。

输出重点：

1. `questionAnsweringReady`
2. `knowledgeBaseStatus`
3. `indexedChunkCount`
4. `embeddedChunkCount`
5. `reembedRequired`
6. `reembedInProgress`
7. `nextStep`

边界：

1. 不触发 rebuild。
2. 不修改知识库状态。
3. 不调用 LLM。

## 关键边界

Day 3 必须继续遵守：

1. Java 是业务权威和工具白名单中心。
2. Day 3 只做 READ_ONLY 工具。
3. Day 3 不实现任何 `REQUIRES_CONFIRMATION` 工具。
4. Day 3 不接 Python LangGraph。
5. Day 3 不让 Agent 自动改变 run 状态。
6. 工具封装必须复用现有服务，不复制业务逻辑。
7. `system.health.check / kb.readiness.check` 都不能产生写副作用。

## 已验证

已新增测试：

1. `AgentToolRegistryTest`
   - 能按名称获取 `system.health.check`
   - 能按名称获取 `kb.readiness.check`
   - 不存在的 toolName 返回空或抛出明确异常
   - 注册工具没有重复 toolName
2. `SystemHealthAgentToolTest`
   - 调用 `SystemHealthService.currentStatus()`
   - 返回 `success=true`
   - 返回 JSON 中包含 health status
3. `QaReadinessAgentToolTest`
   - 调用 `QuestionAnsweringService.getReadiness(kbCode)`
   - 返回 `success=true`
   - 返回 JSON 中包含 `questionAnsweringReady / reembedRequired / nextStep`

执行验证：

```text
mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,SystemHealthAgentToolTest,QaReadinessAgentToolTest test
mvn -q -pl rag-backend -DskipTests compile
```

两项均已通过。

同时修复了 `AgentControllerTest` 中 `MediaType.APPLICATION_JSON` 触发的 null-safety IDE 诊断，改为显式构造非空 `MediaType` 常量。

## 验收结果

1. 后端可编译。
2. Agent 工具白名单可以列出两个 P0 工具。
3. `system.health.check` 可以返回结构化 JSON 结果。
4. `kb.readiness.check` 可以返回结构化 JSON 结果。
5. 两个工具的 `executionMode` 都是 `READ_ONLY`。
6. 两个工具的 `maxRiskLevel` 都是 `LOW`。
7. Day 3 未接 Python Runtime，符合计划边界。
8. Day 3 未持久化 tool step，保留到 Day 5。

## 下一步

Day 4 将进入 Python Agent Runtime：

1. 在 `rag-ai-service` 引入 LangGraph。
2. 基于 Day 1 的 `AgentState` 实现最小图：
   - `parse_goal`
   - `system_health_check`
   - `kb_readiness_check`
   - `diagnose`
   - `recommend_actions`
   - `generate_report`
3. 先跑通最小诊断图，不接 Java run 落库。
