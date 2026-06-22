# Day 4：Python LangGraph 最小诊断图

## 目标

Day 4 的目标是在 `rag-ai-service` 内落下第一版 LangGraph Agent Runtime，让 Python 侧可以独立跑通一次最小诊断流程。

本日重点不是接 Java 落库，也不是完成完整运维闭环，而是先把 Agent Runtime 的计算边界站稳：

1. 引入 LangGraph。
2. 基于 Day 1 的 `AgentState` 实现最小状态图。
3. 暴露 `POST /v1/agent/runs`。
4. 生成稳定的 `steps / recommendedActions / summary` 响应。
5. 保持 Python 不生成 `runCode / stepCode / actionCode`。

Day 4 已覆盖最小图：

```text
parse_goal
  -> system_health_check
  -> kb_readiness_check
  -> diagnose
  -> recommend_actions
  -> generate_report
```

`documents_status_scan / indexing_tasks_scan / qa_retrieve_probe` 暂不进入 Day 4，保留到 Day 6 和 Day 13。

## 项目背景

根目录 `README.md` 已明确当前项目不是设计阶段，而是已经完成企业知识库 RAG 主链路的第一版实现：

1. Java 后端负责知识库、文档、异步索引、向量检索、问答、readiness、rebuild 和恢复语义。
2. React 前端已经形成工作台，覆盖知识库、文档、检索、问答、历史和健康页。
3. Python `rag-ai-service` 当前是 AI Gateway，统一承接 embeddings 和 chat completions。
4. Week 4 已完成第一版 `DENSE / HYBRID` 检索、真实评测和最小观测口径。

因此 Day 4 的 Agent 改造不能重新设计 RAG 主链路，也不能把 Python 做成新的业务权威。LangGraph 只是在现有系统上方增加一次诊断编排能力。

## 当前输入

Day 1 已完成：

1. `AgentRuntimeRequest`
2. `AgentRuntimeResponse`
3. `AgentRuntimeStepResult`
4. `AgentRuntimeActionDraft`
5. Python 侧 `AgentState`
6. Java / Python Runtime 协议草案

Day 2 已完成：

1. Java Agent run 创建 API。
2. Java Agent run 详情查询 API。
3. Java 统一生成 `runCode`。
4. `stepCode / actionCode` 暂未生成，留给 Day 5 落库阶段。

Day 3 已完成：

1. Java Agent 工具抽象。
2. Java Agent 工具白名单注册表。
3. `system.health.check`
4. `kb.readiness.check`

Python 当前状态：

1. `rag-ai-service/app/agent/state.py` 已存在。
2. `rag-ai-service/app/api/routes.py` 只暴露：
   - `/health`
   - `/v1/embeddings`
   - `/v1/chat/completions`
3. `rag-ai-service/pyproject.toml` 尚未引入 LangGraph。
4. 还没有 Agent Runtime service、graph builder 或 `/v1/agent/runs`。

## 关键边界

Day 4 必须继续遵守：

1. Java 是业务权威和 Agent Run 状态中心。
2. Python 只是 LangGraph Agent Runtime。
3. Python 不生成 `runCode / stepCode / actionCode`。
4. Python 不直接写业务库。
5. Python 不直接执行重试、重嵌入、启用知识库等写操作。
6. 写操作只能作为 `recommendedActions` 草案返回，后续由 Java 落库、确认和执行。
7. `WAITING_CONFIRMATION` 是 Java run 状态，Python Runtime 不返回该状态。
8. Day 4 不实现 confirm/reject。
9. Day 4 不接前端。
10. Day 4 不把 Agent 能力写进根 README 的已完成列表。

## 已新增文件

已新增：

```text
rag-ai-service/app/agent/runtime.py
rag-ai-service/app/agent/graph.py
rag-ai-service/tests/test_agent_runtime.py
```

`tools.py` 当前承接 Day 4 可替换工具客户端，避免后续 Day 5 接 Java HTTP 工具时侵入 LangGraph 节点逻辑。

## 已修改文件

已修改：

```text
rag-ai-service/pyproject.toml
rag-ai-service/app/api/routes.py
rag-ai-service/app/agent/__init__.py
```

修改意图：

1. `pyproject.toml` 增加 `langgraph` 依赖。
2. `routes.py` 增加 `POST /v1/agent/runs`。
3. `__init__.py` 导出 Runtime 入口或保持包初始化。

## Runtime API

Day 4 已暴露：

```text
POST /v1/agent/runs
```

请求沿用 Day 1 协议：

```json
{
  "runCode": "AR-xxx",
  "kbCode": "day20-cn-kb",
  "goal": "诊断这个知识库为什么不能问答",
  "question": "可选检索探测问题",
  "runMode": "DIAGNOSE_AND_RECOMMEND"
}
```

响应沿用 Day 1 协议：

```json
{
  "status": "SUCCEEDED",
  "summary": "知识库当前不可问答，主要原因是 embedding 配置变化后尚未重嵌入。",
  "steps": [],
  "recommendedActions": []
}
```

注意：

1. `status` 只返回 `SUCCEEDED / FAILED`。
2. `steps` 不包含 `stepCode`。
3. `recommendedActions` 不包含 `actionCode`。
4. action 只返回 `toolName / title / reason / riskLevel / requiresConfirmation / actionPayload`。

## LangGraph 节点设计

### parse_goal

职责：

1. 记录原始目标。
2. 基于 `goal / question / runMode` 做最小任务分类。
3. 不调用 LLM。

输出 step：

```text
nodeName = parse_goal
stepType = NODE
status = SUCCEEDED
```

Day 4 可以先用规则判断：

1. goal 包含“不能问答 / readiness / 问答”时，进入 readiness 诊断主线。
2. 其他 goal 也先走最小 health + readiness 诊断。

### system_health_check

职责：

1. 调用 `system.health.check` 工具边界。
2. 记录工具调用结果。
3. 把结果写入 `AgentState.tool_results["system.health.check"]`。

Day 4 当前通过 `StaticAgentToolClient` 返回受控样例结果，用来验证 LangGraph Runtime 形状。该客户端是可替换边界，Day 5 后续应替换为 Java HTTP 工具调用。

### kb_readiness_check

职责：

1. 调用 `kb.readiness.check` 工具边界。
2. 读取 `reembedRequired / questionAnsweringReady / nextStep`。
3. 把结果写入 `AgentState.tool_results["kb.readiness.check"]`。

Day 4 的核心演示信号是：

```text
reembedRequired = true
```

只要 readiness 结果里出现该信号，后续 `recommend_actions` 应生成 `embedding.rebuild.submit` action 草案。

### diagnose

职责：

1. 汇总 health 和 readiness 结果。
2. 产出结构化诊断结论。
3. 不做权限判断。
4. 不生成最终 action code。

最小规则：

1. health 异常时，summary 指向系统依赖异常。
2. readiness 显示 `reembedRequired=true` 时，summary 指向 embedding profile 变化后需要重嵌入。
3. readiness 显示 `questionAnsweringReady=true` 且 health 正常时，summary 指向当前未发现阻断性问题。

### recommend_actions

职责：

1. 根据诊断结果生成 action 草案。
2. 只生成推荐，不执行。
3. 所有写操作都必须 `requiresConfirmation=true`。

Day 4 最小动作：

```text
toolName = embedding.rebuild.submit
title = 提交知识库重嵌入任务
riskLevel = MEDIUM
requiresConfirmation = true
```

触发条件：

```text
kb.readiness.check.output.reembedRequired == true
```

`actionPayload` 建议使用 JSON 字符串：

```json
{
  "kbCode": "day20-cn-kb"
}
```

注意：

1. Day 4 可以返回 `embedding.rebuild.submit` 草案。
2. Day 4 不实现该工具。
3. Day 4 不确认、不执行。
4. Java 后续必须按白名单和确认状态处理该 action。

### generate_report

职责：

1. 生成最终 `summary`。
2. 汇总 steps 和 recommendedActions。
3. 返回 `AgentRuntimeResponse`。

Day 4 先用规则模板生成报告，不调用 LLM 润色。

## 工具调用边界

Day 4 最容易出错的点是过早把 Python 写成业务工具执行中心。正确边界是：

1. Java 封装真实工具能力。
2. Python 通过工具协议请求工具结果。
3. Python 根据工具结果诊断和推荐。
4. Java 决定 run 状态、生成 code、落库和确认执行。

如果 Day 4 暂时没有 Java 内部工具 HTTP API，则文档和代码都要显式表达：

```text
Tool Client is replaceable.
Day 4 only validates LangGraph runtime shape.
Day 5 connects Java runtime invocation and persistence.
```

不能把替身实现伪装成真实系统能力。

## 已验证

Python 测试已覆盖：

1. `POST /v1/agent/runs` 返回 200。
2. 响应不包含 `stepCode / actionCode`。
3. 最小图按预期生成节点：
   - `parse_goal`
   - `system_health_check`
   - `kb_readiness_check`
   - `diagnose`
   - `recommend_actions`
   - `generate_report`
4. `reembedRequired=true` 时返回 `embedding.rebuild.submit` action 草案。
5. `runMode=DIAGNOSE_ONLY` 时不返回写操作推荐，或明确跳过 `recommend_actions` 写动作输出。
6. 工具调用失败时 Runtime 返回 `FAILED`，并带 `errorMessage`。

已执行验证：

```text
./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py
./venv/bin/python -m py_compile rag-ai-service/app/agent/state.py rag-ai-service/app/agent/runtime.py rag-ai-service/app/agent/graph.py
```

新增 `langgraph` 依赖后，当前虚拟环境已执行：

```text
./venv/bin/pip install -e rag-ai-service
```

## 验收结果

Day 4 已满足：

1. `rag-ai-service` 可以启动。
2. `POST /v1/agent/runs` 可以返回符合协议的 `AgentRuntimeResponse`。
3. LangGraph 最小图可执行。
4. Runtime response 的 steps 可表达节点轨迹。
5. Runtime response 的 recommendedActions 可表达 `embedding.rebuild.submit` 草案。
6. Python 不生成 `runCode / stepCode / actionCode`。
7. Python 不写业务库。
8. Python 不执行任何写操作。
9. 测试覆盖最小成功路径和 `reembedRequired=true` 推荐路径。
10. 完成后更新 `docs/work/rag-agent/current-status.md`。

## 暂不做

Day 4 暂不做：

1. Java 调用 Python Runtime。
2. Python 返回结果落库。
3. `documents.status.scan`。
4. `indexing.tasks.scan`。
5. `document.indexing_task.retry`。
6. `embedding.rebuild.submit` 的真实执行。
7. confirm/reject API。
8. 前端 Agent 工作台。
9. `qa.retrieve.probe`。
10. LLM 润色报告。

## 下一步

Day 5 进入 Java 与 Python Runtime 串联：

1. Java 创建 run 后调用 `POST /v1/agent/runs`。
2. Java 接收 Python 返回的 steps/actions。
3. Java 统一生成 `stepCode / actionCode`。
4. Java 持久化 `agent_step / agent_action`。
5. Java 根据 action 是否需要确认决定 run 状态：
   - 有待确认 action：`WAITING_CONFIRMATION`
   - 无待确认 action：`SUCCEEDED`
   - Runtime 失败：`FAILED`
