# Day 18：LLM 决策校验和失败恢复

## 目标

Day 18 的目标是把智能 Agent 的 `llm_plan` 从“能解析 JSON”推进到“可测试、可恢复、可防御”的决策校验层。

重点不是接真实 LLM provider，而是把 fake/mock LLM 的确定性测试补齐，固定这些行为：

```text
LLM raw output
  -> JSON parse
  -> AgentDecision schema 校验
  -> action 枚举校验
  -> toolName 白名单校验
  -> arguments schema 校验
  -> 失败重试一次
  -> 仍失败则 FAILED
```

## 当前输入

Day 17 已完成：

1. `build_intelligent_tool_agent_graph()`。
2. `HeuristicAgentDecisionClient`。
3. `LLM_DECISION` step。
4. `execute_readonly_tool` 和 `create_recommended_action`。
5. fake MCP tool 和只读 CLI tool。
6. 非法 JSON 重试一次的基础能力。

## 边界约束

Day 18 必须遵守：

1. 不记录 chain-of-thought。
2. 不依赖真实模型稳定性，测试使用 fake/mock LLM。
3. 不允许未知 toolName 进入工具执行。
4. 不允许明显不符合 schema 的 arguments 进入工具执行。
5. 工具调用循环必须有上限。
6. 失败要写入 `LLM_DECISION` 或 `fail_report` step，便于前端 timeline 解释。

## AgentDecision 校验规则

### JSON parse

输入必须是合法 JSON。

非法 JSON：

1. 第一次失败：重试一次。
2. 第二次仍失败：返回 `FAILED`。
3. 写入失败 `LLM_DECISION` step。

### action 校验

只允许：

```text
CALL_TOOL
REQUEST_CONFIRMATION
FINAL_ANSWER
```

### FINAL_ANSWER

必须包含非空：

```text
finalAnswer
```

不要求 `toolName`。

### CALL_TOOL / REQUEST_CONFIRMATION

必须包含：

```text
toolName
```

且 `toolName` 必须存在于当前 `state.tools`。

### arguments schema

Day 18 先实现最小 schema 校验：

1. 校验 required 字段。
2. 校验 string / number / boolean 基础类型。
3. 未知字段先不全局拒绝，避免早期 schema 太粗导致工具扩展困难。

更完整的 JSON Schema validator 可以留到后续增强。

## 循环上限

智能 Agent 默认最多执行 6 次工具调用：

```text
max tool call count = 6
```

超过后进入 `fail_report`，summary 写明超限原因。

## 实施内容

文件：

```text
rag-ai-service/app/agent/graph.py
rag-ai-service/tests/test_agent_runtime.py
```

已完成：

1. `llm_plan` 对非法 JSON 重试一次。
2. `_validate_decision(...)` 校验 action 语义、toolName 白名单和 arguments。
3. `_validate_arguments(...)` 校验 required 和基础类型。
4. `_route_after_decision(...)` 校验工具循环上限。
5. 新增 fake/mock LLM 测试：
   - 非法 JSON 重试后失败。
   - 未知 toolName 被拒绝。
   - arguments schema 不匹配被拒绝。
   - 超过工具调用上限后失败。

## 当前完成度核对

已完成：

1. 决策 JSON parse 失败恢复。
2. toolName 白名单校验。
3. arguments 基础 schema 校验。
4. 最大工具调用次数失败保护。
5. fake/mock LLM 确定性测试。

尚未完成：

1. 真实 LLM planner。
2. 完整 JSON Schema validator。
3. prompt 模板和模型调用观测字段。
4. timeoutMs 在各 adapter 中的统一执行中断。

## 验收标准

Day 18 完成时应满足：

1. 非法 JSON 不会导致 Runtime 崩溃。
2. 未知 toolName 不会进入工具执行。
3. arguments 类型不符合 schema 时不会进入工具执行。
4. 工具循环超限会失败并写明原因。
5. legacy 固定图测试继续通过。

## 已验证

```text
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
./venv/bin/python -m py_compile rag-ai-service/app/agent/state.py rag-ai-service/app/agent/tools.py rag-ai-service/app/agent/graph.py rag-ai-service/app/agent/runtime.py
```

## 下一步

Day 19 进入安全拦截和 Java 落库边界：

1. 验证 Python `recommendedActions` 由 Java 统一生成 `actionCode`。
2. 验证智能模式有 recommended action 时 run 进入 `WAITING_CONFIRMATION`。
3. 验证 `LLM_DECISION` step 可以被 Java 持久化。
