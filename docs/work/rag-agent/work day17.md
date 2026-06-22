# Day 17：智能 LangGraph 主循环骨架

## 目标

Day 17 的目标是新增智能 Tool-use Agent 的 LangGraph 主循环骨架，让 Runtime 能在 `INTELLIGENT_TOOL_AGENT` 模式下完成：

```text
加载工具
  -> 生成结构化决策
  -> 路由决策
  -> 执行只读工具或生成待确认动作
  -> 回到下一轮决策或结束
```

Day 17 先用 deterministic `HeuristicAgentDecisionClient` 和测试 fake/mock decision client，不接真实 LLM provider。真实 LLM planner、完整 JSON parser 加固和 schema 深化留给 Day 18。

## 当前输入

Day 15-16 已完成：

1. 新 runMode：`INTELLIGENT_TOOL_AGENT`。
2. 新 stepType：`LLM_DECISION`。
3. Python AgentState 智能字段。
4. ToolDefinition v2。
5. Java 内部 tool definitions 查询接口。
6. Python `AgentToolClient.definitions()`。

## 边界约束

Day 17 必须遵守：

1. 保留旧固定诊断图。
2. 智能图是新增入口，不替代 legacy 图。
3. 不接真实 LLM。
4. 不执行写工具。
5. `CALL_TOOL` 如果命中需要确认的工具，必须转成 `recommendedActions`。
6. MCP/CLI 仍是 fake/static MVP。
7. observation 回灌 LLM 前要有裁剪/摘要结构。

## 图结构

新增智能图：

```text
load_tools
  -> llm_plan
  -> route_decision
      -> execute_readonly_tool -> llm_plan
      -> create_recommended_action -> END
      -> final_report -> END
      -> fail_report -> END
```

文件：

```text
rag-ai-service/app/agent/graph.py
```

保留旧图：

```text
build_readiness_diagnosis_graph()
```

新增智能图：

```text
build_intelligent_tool_agent_graph()
```

兼容入口：

```text
build_agent_graph() -> build_readiness_diagnosis_graph()
```

## 节点职责

### load_tools

职责：

1. 调用 `tool_client.definitions()`。
2. 写入 `state.tools`。
3. 初始化最小 `messages`。

### llm_plan

职责：

1. 调用 `decision_client.decide(state)`。
2. 解析严格 JSON 为 `AgentDecision`。
3. 校验 action / toolName / arguments。
4. 落 `LLM_DECISION` step。

当前实现：

1. 使用 `HeuristicAgentDecisionClient` 作为 deterministic planner。
2. 支持非法 JSON 重试一次。
3. 失败时写 `error_message` 并落失败 step。

### route_decision

职责：

根据当前 `decision` 分流：

1. `FINAL_ANSWER` -> `final_report`
2. `REQUEST_CONFIRMATION` -> `create_recommended_action`
3. `CALL_TOOL + READ_ONLY/LOW/no confirmation` -> `execute_readonly_tool`
4. `CALL_TOOL + WRITE/MEDIUM/HIGH/requiresConfirmation` -> `create_recommended_action`
5. 错误或超限 -> `fail_report`

工具调用上限：

```text
max tool call count = 6
```

### execute_readonly_tool

职责：

1. 执行只读工具。
2. 保存 raw output 到 step output JSON。
3. 生成 `summaryForLlm`。
4. 写入 `observations`。
5. `tool_call_count + 1`。
6. 回到 `llm_plan`。

### create_recommended_action

职责：

1. 将当前 decision 转成 Python `AgentActionDraft`。
2. 写入 `recommended_actions`。
3. summary 标记为已生成待确认动作。
4. 结束图执行。

Java 后续会统一生成：

```text
actionCode
PENDING_CONFIRMATION
WAITING_CONFIRMATION
```

### final_report

职责：

1. 使用 `decision.finalAnswer` 生成 summary。
2. 不生成 action。
3. run 最终由 Java 视为 `SUCCEEDED`。

### fail_report

职责：

1. 生成失败 summary。
2. 写入 `error_message`。
3. run 最终由 Java 视为 `FAILED`。

## Deterministic Planner

Day 17 新增 `HeuristicAgentDecisionClient`，用于在未接真实 LLM 前跑通智能主循环。

当前支持三个固定场景：

1. readiness 场景：
   - 调用 `kb.readiness.check`
   - 如果 `reembedRequired=true`，尝试 `embedding.rebuild.submit`
   - Runtime 强制转成 recommended action
2. 索引异常场景：
   - 调用 `indexing.tasks.scan`
   - 如果存在 failed task，尝试 `document.indexing_task.retry`
   - Runtime 强制转成 recommended action
3. 项目状态场景：
   - 调用 `mcp.repo.status.inspect`
   - 调用 `cli.git.status`
   - 生成 `FINAL_ANSWER`

## 安全拦截

Day 17 已实现 Runtime 层强制拦截：

```text
requiresConfirmation == true
or executionMode != READ_ONLY
or riskLevel in MEDIUM/HIGH
```

满足任一条件时，即使 planner 输出 `CALL_TOOL`，也不会执行工具，而是进入 `create_recommended_action`。

## 测试

已新增/更新 Python 测试：

1. 智能 Agent readiness 场景会把 `embedding.rebuild.submit` 转成 recommended action。
2. 智能 Agent 项目状态场景会依次调用 fake MCP tool 和只读 CLI tool，然后生成 final answer。
3. 非法 JSON 会重试一次，仍失败则返回 `FAILED`。
4. legacy 固定图原有测试继续通过。

已验证：

```text
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
./venv/bin/python -m py_compile rag-ai-service/app/agent/state.py rag-ai-service/app/agent/tools.py rag-ai-service/app/agent/graph.py rag-ai-service/app/agent/runtime.py
```

后端回归已验证：

```text
mvn -q -pl rag-backend -Dtest=AgentInternalToolControllerTest,AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest,QaRetrieveProbeAgentToolTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test
mvn -q -pl rag-backend -DskipTests compile
```

## 当前完成度核对

已完成：

1. legacy 固定图和 intelligent 图并存。
2. `INTELLIGENT_TOOL_AGENT` 可切换到智能图。
3. `LLM_DECISION` step 可返回给 Java 落库。
4. 只读工具可循环调用。
5. recommended action 可由 Python 返回。
6. fake MCP tool 和只读 CLI tool 已进入同一主循环。
7. observation 已拆成 raw output 和 `summaryForLlm`。

尚未完成：

1. 真实 LLM planner。
2. 更严格的 JSON schema validator。
3. Java 侧智能模式 WAITING_CONFIRMATION 端到端专项测试。
4. MCP/CLI 配置化。
5. 前端 runMode 选择项中展示 `INTELLIGENT_TOOL_AGENT`。

## 下一步

Day 18 继续：

1. 把 `HeuristicAgentDecisionClient` 替换为可接真实 LLM 的 planner 抽象。
2. 强化 AgentDecision parser。
3. 深化 arguments schema 校验。
4. 补充 fake LLM / mock LLM 的失败恢复测试。

Day 19 继续：

1. Java 侧补智能模式 recommended action -> `WAITING_CONFIRMATION` 的端到端测试。
2. 确认 Python returned `recommendedActions` 到 Java 落库边界稳定。
