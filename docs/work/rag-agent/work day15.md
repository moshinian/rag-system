# Day 15：智能 Tool-use Agent 状态模型与计划收口

## 目标

Day 15 的目标是把第 3 周方向从“固定流程式 RAG 运维诊断 Agent”正式调整为“单 Agent 智能 Tool-use Agent”，并完成最小状态模型、运行模式、step 类型和文档计划收口。

Day 15 不要求接真实 LLM，也不要求接真实 MCP server。它的重点是先把架构边界、状态字段和兼容策略定住：

```text
legacy 固定诊断图
  + intelligent tool-use 图
  + 统一 ToolDefinition v2
  + LLM_DECISION / TOOL_CALL 审计轨迹
```

## 当前输入

前 14 天已经具备：

1. Java `agent_run / agent_step / agent_action` 三张表和落库模型。
2. Python LangGraph 固定诊断图。
3. Java 侧 Agent tools：
   - `system.health.check`
   - `kb.readiness.check`
   - `documents.status.scan`
   - `indexing.tasks.scan`
   - `qa.retrieve.probe`
4. Java confirm/reject 与写动作白名单：
   - `document.indexing_task.retry`
   - `embedding.rebuild.submit`
5. 前端 Agent 工作台、timeline、action cards、confirm/reject。

第 3 周的新目标是补“智能 Agent”能力，而不是删除已有确定性工作流。

## 边界约束

Day 15 必须遵守：

1. 保留旧固定流程图作为 legacy/debug。
2. 新增智能图，不替换旧图。
3. 仍然是单 Agent，不引入多 Agent。
4. LLM 决策只能落结构化 `LLM_DECISION` step，不记录 chain-of-thought。
5. Java 继续是 run/action 编码、状态中心和写操作执行权威。
6. Python Runtime 可以返回 `recommendedActions`，但不生成 `actionCode`。
7. 写操作仍必须 human-in-the-loop。

## 状态模型调整

### runMode

新增：

```text
INTELLIGENT_TOOL_AGENT
```

保留：

```text
DIAGNOSE_ONLY
DIAGNOSE_AND_RECOMMEND
```

含义：

1. `DIAGNOSE_ONLY / DIAGNOSE_AND_RECOMMEND` 继续走 legacy 固定诊断图。
2. `INTELLIGENT_TOOL_AGENT` 走智能 Tool-use 图。

### stepType

新增：

```text
LLM_DECISION
```

保留：

```text
NODE
TOOL_CALL
REASONING
```

`LLM_DECISION` 只保存：

1. LLM 输出的结构化 JSON 决策。
2. reason 摘要。
3. 校验结果。
4. 错误信息。

不保存模型 chain-of-thought。

### Python AgentState

智能 Agent State 显式补入：

```text
tools
messages
decision
observations
tool_call_count
steps
recommended_actions
summary
error_message
```

这些字段的职责：

1. `tools`：当前 run 可见的 ToolDefinition v2 列表。
2. `messages`：给 LLM planner 的最小消息上下文。
3. `decision`：当前轮 AgentDecision。
4. `observations`：工具执行后的结构化观察。
5. `tool_call_count`：防止无限循环。
6. `steps`：返回 Java 落库的轨迹。
7. `recommended_actions`：返回 Java 落库的待确认动作草案。

## AgentDecision 协议

第 3 周采用严格 JSON 决策协议：

```text
CALL_TOOL
REQUEST_CONFIRMATION
FINAL_ANSWER
```

Day 15 先在 `plan.md` 中写入三类 JSON 示例，供 Day 18 parser 和测试实现使用。

## 文档调整

Day 15 已更新：

1. `docs/work/rag-agent/plan.md`
   - 增加第 3 周“单 Agent 智能 Tool-use 改造”。
   - 写入 run status 规则。
   - 写入智能 LangGraph 图结构。
   - 写入 AgentState 字段。
   - 写入 ToolDefinition v2 字段。
   - 写入 AgentDecision JSON 示例。
   - 写入 Day 15-21 任务拆分。
2. `docs/work/rag-agent/README.md`
   - Current Breakpoint 改为第 3 周智能 Tool-use Agent。
3. `docs/work/rag-agent/current-status.md`
   - 写入 Day 15 基础骨架完成情况。
   - 写清未接真实 LLM planner / 真实 MCP server。

## 代码调整

Day 15 已完成：

1. Java 新增 `AgentRunMode.INTELLIGENT_TOOL_AGENT`。
2. Java 新增 `AgentStepType.LLM_DECISION`。
3. Python `AgentRunMode` 新增 `INTELLIGENT_TOOL_AGENT`。
4. Python `AgentStepType` 新增 `LLM_DECISION`。
5. Python 新增模型：
   - `AgentToolDefinition`
   - `AgentDecision`
   - `AgentObservation`
6. Python `AgentState` 补入智能 Agent 所需字段。

## 验收标准

Day 15 完成时应满足：

1. legacy 固定诊断图仍可运行。
2. 新 runMode / stepType 能被 Java 和 Python 模型识别。
3. `plan.md` 已明确第 3 周 Day 15-21 的任务。
4. `current-status.md` 已写清当前断点。
5. 不把真实 LLM / 真实 MCP / 完整 CLI 误写成已完成。

## Day 15 执行记录

已完成：

1. 更新 `docs/work/rag-agent/plan.md`。
2. 更新 `docs/work/rag-agent/README.md`。
3. 更新 `docs/work/rag-agent/current-status.md`。
4. 扩展 Java `AgentRunMode / AgentStepType`。
5. 扩展 Python `state.py`。

已验证：

```text
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
mvn -q -pl rag-backend -DskipTests compile
git diff --check
```

## 下一步

Day 16 进入 Tool Registry v2：

1. Java 暴露内部 tool definitions 查询接口。
2. ToolDefinition 补齐 v2 字段。
3. Python Runtime 能发现 Java tools。
4. 保持 read-only execute 接口只允许只读工具。
