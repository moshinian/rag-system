# Day 16：Tool Registry v2 与工具发现接口

## 目标

Day 16 的目标是把 Agent 可见工具从“Python 代码里隐含知道工具名”升级为“Runtime 可发现的 ToolDefinition v2”。

这一天的重点是：

```text
Java AgentToolRegistry
  -> GET /api/internal/agent/tools
  -> Python AgentToolClient.definitions()
  -> intelligent Agent load_tools
```

Day 16 仍不接真实 MCP server，也不做完整 CLI adapter 配置化。MCP/CLI 的最小样例先作为静态工具定义，为 Day 20 做准备。

## 当前输入

Day 15 已完成：

1. `INTELLIGENT_TOOL_AGENT` runMode。
2. `LLM_DECISION` stepType。
3. Python `AgentToolDefinition / AgentDecision / AgentObservation`。
4. 第 3 周计划写入 `plan.md`。

现有 Java 工具基础：

1. `AgentTool`
2. `AgentToolRegistry`
3. `AgentToolDefinition`
4. `AgentInternalToolController`
5. `POST /api/internal/agent/tools/{toolName}/execute`

## 边界约束

Day 16 必须继续遵守：

1. Python Runtime 只能直接执行 `READ_ONLY` Java tools。
2. 写工具不能通过 internal execute 接口执行。
3. ToolDefinition 是 LLM planner 的可见工具契约，不等于执行授权。
4. Java 仍然是业务工具权威。
5. MCP/CLI 第 16 天只准备静态定义，不接真实外部进程或 server。

## ToolDefinition v2

Day 16 将 Java `AgentToolDefinition` 扩展为 v2：

```text
toolName
schemaVersion
description
inputSchema
outputSchema
executionMode
maxRiskLevel
sourceType
requiresConfirmation
timeoutMs
```

字段含义：

1. `schemaVersion`：当前固定为 `v2`。
2. `description`：给 LLM planner 的工具说明。
3. `inputSchema`：arguments 校验依据。
4. `outputSchema`：帮助 LLM 理解 observation 结构。
5. `executionMode`：只读或需要确认。
6. `maxRiskLevel`：工具最高风险等级。
7. `sourceType`：`JAVA / MCP / CLI`。
8. `requiresConfirmation`：是否必须人审。
9. `timeoutMs`：工具调用超时预算。

当前 Java 工具类仍使用三参数构造器，默认补齐：

```text
schemaVersion = v2
description = toolName
inputSchema = {}
outputSchema = {}
sourceType = JAVA
requiresConfirmation = executionMode != READ_ONLY || maxRiskLevel != LOW
timeoutMs = 5000
```

后续 Day 18/20 可以逐步把 schema 写得更精细。

## Java 实施

### 1. 扩展 DTO

文件：

```text
rag-backend/src/main/java/com/example/rag/model/dto/AgentToolDefinition.java
```

已完成：

1. 增加 v2 字段。
2. 保留原三参数构造器，避免所有现有工具类一次性大改。

### 2. 新增工具定义查询接口

文件：

```text
rag-backend/src/main/java/com/example/rag/controller/AgentInternalToolController.java
```

新增：

```text
GET /api/internal/agent/tools
```

行为：

1. 要求 `X-Agent-Tool-Token`。
2. 返回 `AgentToolRegistry.definitions()`。
3. 返回内容用于 Python Runtime 的 `load_tools`。

### 3. 保持只读执行接口

既有接口保持：

```text
POST /api/internal/agent/tools/{toolName}/execute
```

继续强制：

```text
tool.definition().executionMode() == READ_ONLY
```

非只读工具继续拒绝。

## Python 实施

文件：

```text
rag-ai-service/app/agent/tools.py
```

已完成：

1. `AgentToolClient` 增加：

```python
def definitions(self) -> list[AgentToolDefinition]
```

2. `StaticAgentToolClient.definitions()` 返回默认工具定义。
3. `JavaAgentToolClient.definitions()` 调用 Java `GET /api/internal/agent/tools`。
4. Java definitions 获取失败时回退到默认工具定义。
5. 默认工具定义包含：
   - Java tools
   - fake MCP tool：`mcp.repo.status.inspect`
   - 只读 CLI tool：`cli.git.status`

## 当前完成度核对

已完成：

1. Java ToolDefinition v2 DTO。
2. Java definitions 查询接口。
3. Python tool definitions 拉取能力。
4. Static fallback definitions。
5. fake MCP / CLI 的静态工具定义。
6. 内部 execute 接口仍只允许 `READ_ONLY`。

尚未完成：

1. Java 每个工具的精细 `inputSchema / outputSchema`。
2. MCP server 的真实工具发现。
3. CLI adapter 的配置化命令模板。
4. timeoutMs 在具体执行层的强制中断。

这些留给 Day 18-20 继续。

## 测试

已新增/更新：

1. `AgentInternalToolControllerTest`
   - `GET /api/internal/agent/tools` 返回 v2 字段。
   - 非只读工具仍被 execute 拒绝。
2. `test_agent_runtime.py`
   - 智能 Agent 可使用 tool definitions。

已验证：

```text
mvn -q -pl rag-backend -Dtest=AgentInternalToolControllerTest,AgentToolRegistryTest,SystemHealthAgentToolTest,QaReadinessAgentToolTest test
mvn -q -pl rag-backend -DskipTests compile
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py
```

## 下一步

Day 17 进入智能 LangGraph 主循环：

1. 新增 `build_intelligent_tool_agent_graph()`。
2. 实现 `load_tools / llm_plan / route_decision / execute_readonly_tool / create_recommended_action / final_report / fail_report`。
3. 每轮 LLM 决策落 `LLM_DECISION` step。
4. 每次工具调用落 `TOOL_CALL` step。
