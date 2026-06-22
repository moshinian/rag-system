# Day 20：MCP/CLI 最小配置化接入

## 目标

Day 20 的目标是把 Day 17 的 fake MCP / 只读 CLI 静态样例推进到“配置化最小 MVP”，证明智能 Agent 主循环不需要改动，就能通过 Tool Registry 增加新的工具来源。

Day 20 只做：

```text
1 个 fake MCP tool
1 个只读 CLI tool
```

不做真实 MCP server，不做通用 shell agent，不做写 CLI。

## 当前输入

Day 17-19 已完成：

1. `build_intelligent_tool_agent_graph()`。
2. ToolDefinition v2。
3. `AgentToolClient.definitions()`。
4. fake MCP tool：`mcp.repo.status.inspect`。
5. 只读 CLI tool：`cli.git.status`。
6. Runtime 安全拦截。
7. Java recommended action 落库边界。

## 边界约束

Day 20 必须遵守：

1. CLI 不是通用 shell 代理。
2. LLM 不能传任意 command。
3. CLI tool 必须白名单、schema 化、模板化。
4. 默认只读。
5. 写 CLI 未来必须走 Java confirm/action 流程。
6. MCP 只做 fake/minimal MVP，用于验证 tool source 接入机制。

## 配置项

新增 Python settings：

```text
agent_fake_mcp_enabled = true
agent_fake_mcp_tool_name = mcp.repo.status.inspect
agent_cli_git_status_enabled = true
agent_cli_git_status_tool_name = cli.git.status
agent_cli_git_status_timeout_ms = 5000
```

含义：

1. fake MCP 可通过配置启用/关闭。
2. CLI git status 可通过配置启用/关闭。
3. CLI 工具名可配置，但执行模板固定。
4. CLI timeout 来自配置。

## MCP 最小 MVP

Day 20 的 fake MCP tool：

```text
mcp.repo.status.inspect
```

职责：

1. 作为 `sourceType = MCP` 的 ToolDefinition 暴露给 Agent。
2. 返回 fake repo inspection observation。
3. 验证新增 MCP tool 不需要改 Agent 主循环。

不做：

1. 不连接真实 MCP server。
2. 不实现 MCP transport。
3. 不做多 server discovery。

## CLI 最小 MVP

Day 20 的只读 CLI tool：

```text
cli.git.status
```

执行模板固定为：

```text
git status --short
```

实现要求：

1. 使用固定参数数组执行，不使用 shell。
2. 忽略 LLM arguments 中的 command 字段。
3. 不允许传入任意 shell 字符串。
4. 输出只返回 stdout/stderr/exitCode 的摘要。
5. timeout 由配置控制。

## 实施内容

文件：

```text
rag-ai-service/app/core/config.py
rag-ai-service/app/agent/tools.py
rag-ai-service/tests/test_agent_runtime.py
```

已完成：

1. settings 增加 fake MCP / CLI 配置项。
2. `StaticAgentToolClient` 可按配置暴露 fake MCP 和只读 CLI definitions。
3. `JavaAgentToolClient` 在合并 Java definitions 时也合并配置化非 Java tools。
4. `JavaAgentToolClient` 在执行配置化 fake MCP / CLI tools 时走 Python 本地白名单适配器，避免错误 POST 到 Java。
5. `cli.git.status` 使用固定命令模板执行 `git status --short`。
6. 前端已支持 `INTELLIGENT_TOOL_AGENT` 运行模式和 `LLM_DECISION` step type。
7. 测试覆盖：
   - 关闭 fake MCP 后 definitions 不包含 MCP tool。
   - CLI tool definition 包含 `sourceType=CLI` 和配置化 timeout。
   - CLI 执行不使用 LLM command 参数，返回固定模板命令。
   - Java client 下配置化 CLI tool 不会 POST 到 Java。
   - 智能 Agent 仍可在同一主循环中使用 fake MCP + CLI。

## 当前完成度核对

已完成：

1. fake MCP tool 配置化。
2. 只读 CLI tool 配置化。
3. CLI 固定模板执行。
4. Tool Registry 不改主循环即可新增 MCP/CLI source。

尚未完成：

1. 真实 MCP server 接入。
2. MCP transport 和 tool discovery。
3. 多 CLI 模板工具。
4. 写 CLI confirm/action 流程。

## 验收标准

Day 20 完成时应满足：

1. fake MCP 和 CLI tools 可通过 settings 控制暴露。
2. `cli.git.status` 不接受任意 command。
3. 智能 Agent 仍能执行 `mcp.repo.status.inspect -> cli.git.status -> FINAL_ANSWER`。
4. 现有 legacy Agent 测试不受影响。

## 已验证

```text
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py
./venv/bin/python -m py_compile rag-ai-service/app/agent/state.py rag-ai-service/app/agent/tools.py rag-ai-service/app/agent/graph.py rag-ai-service/app/agent/runtime.py
mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentRunScenarioTest,AgentInternalToolControllerTest test
cd rag-frontend && npm run build
git diff --check
```

## 联调结果

已通过前端 Vite proxy 创建智能 Agent run：

```text
POST http://127.0.0.1:5173/api/knowledge-bases/day20-cn-kb/agent/runs
runMode = INTELLIGENT_TOOL_AGENT
runCode = AR-327301374603825153
status = SUCCEEDED
```

实际 timeline：

```text
LLM_DECISION -> mcp.repo.status.inspect -> LLM_DECISION -> cli.git.status -> LLM_DECISION -> final_report
```

结论：

1. frontend -> backend -> ai-service 链路已打通。
2. Java Tool Registry definitions 已被 Python 使用。
3. fake MCP / 只读 CLI 可在同一智能主循环中执行。
4. 本次 run 未生成 recommended actions，符合只读工具演示预期。

## 下一步

Day 21 进入端到端验收和面试材料：

1. 准备 3 个固定演示问题。
2. 准备确定性测试数据。
3. 更新 README、架构说明、简历 bullet 和面试讲稿。
