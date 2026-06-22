# Day 1：状态模型、工具协议和三张表

## 目标

Day 1 的目标是为 LangGraph RAG 运维诊断 Agent 落下最小基础模型：

1. `AgentState` 草案。
2. Java 与 Python Agent Runtime 的工具协议。
3. Agent 状态枚举。
4. `agent_run / agent_step / agent_action` 三张表。

本日不实现 Agent API，不接 LangGraph 状态图，也不做前端页面。

## 已完成

### 后端数据库模型

新增 Flyway 迁移：

1. `V18__create_agent_tables.sql`

该迁移创建：

1. `agent_run`
2. `agent_step`
3. `agent_action`

当前设计继续遵守既定边界：

1. Java 是 Agent Run 权威状态中心。
2. `run_code / step_code / action_code` 作为对外业务编码。
3. Python 不生成最终业务编码。
4. 推荐动作通过 `agent_action` 单独建模，不塞进 step。

### Java 状态模型

新增 Agent 枚举：

1. `AgentRunMode`
2. `AgentRunStatus`
3. `AgentStepType`
4. `AgentStepStatus`
5. `AgentActionRiskLevel`
6. `AgentActionStatus`
7. `AgentToolExecutionMode`

其中 `AgentActionRiskLevel` 继续承载 v1 风险策略：

1. `LOW`：允许确认执行。
2. `MEDIUM`：必须确认执行。
3. `HIGH`：v1 禁止执行，只能展示建议。

### Java 持久化模型

新增实体、Mapper 和 Repository：

1. `AgentRunEntity / AgentRunMapper / AgentRunRepository`
2. `AgentStepEntity / AgentStepMapper / AgentStepRepository`
3. `AgentActionEntity / AgentActionMapper / AgentActionRepository`

当前 Repository 只提供基础插入、更新和查询能力，为 Day 2 的 `AgentRunService` 和查询 API 做准备。

### Java / Python Runtime 协议

新增第一版 Java DTO：

1. `AgentRuntimeRequest`
2. `AgentRuntimeResponse`
3. `AgentRuntimeStepResult`
4. `AgentRuntimeActionDraft`
5. `AgentToolDefinition`

这些 DTO 表达 Java 调用 Python Agent Runtime 时的最小契约：

1. Java 发送 `runCode / kbCode / goal / question / runMode`。
2. Python 返回 step 结果和推荐动作草案。
3. Python 不返回 `stepCode / actionCode`。

### Python AgentState 草案

在 `rag-ai-service` 新增：

1. `app/agent/__init__.py`
2. `app/agent/state.py`

当前 Python 侧先定义：

1. `AgentRuntimeRequest`
2. `AgentStepResult`
3. `AgentActionDraft`
4. `AgentRuntimeResponse`
5. `AgentState`

后续 Day 4 会基于这些模型接入 LangGraph。

## 已验证

1. `mvn -q -pl rag-backend -DskipTests compile`
2. `./venv/bin/python -m py_compile rag-ai-service/app/agent/__init__.py rag-ai-service/app/agent/state.py`

两项均已通过。

## 下一步

进入 Day 2：

1. Java 实现 `agent_run / agent_step / agent_action` 持久化与查询 API。
2. 新增 Agent 请求/响应对象。
3. 新增 `AgentController` 与 `AgentRunService` 骨架。
4. 保持 Java 统一生成 `runCode / stepCode / actionCode` 的边界。
