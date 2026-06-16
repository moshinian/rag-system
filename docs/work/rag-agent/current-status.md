# 当前状态

## 当前结论

`rag-agent` 目前已经完成 Day 2 的 Agent 查询 API 与 Service 骨架，进入 Day 3 前的状态。

当前已经明确的方向是：

**基于 LangGraph 的 RAG 运维诊断 Agent。**

它不是替代现有 RAG 主链路，也不是新增一个泛聊天机器人，而是在已有 RAG 系统上方增加 Agent 编排层：

1. Java 后端继续作为业务权威和 Agent Run 状态中心。
2. Python `rag-ai-service` 新增 LangGraph Agent Runtime。
3. 前端新增 Agent 工作台，展示诊断轨迹、推荐动作和确认执行。
4. v1 聚焦两个可演示业务场景：`reembedRequired` 和 `FAILED indexing task`。

## 已完成

### Phase 0：计划和边界收口

1. 已确认 Agent 主场景是“RAG 运维诊断 Agent”。
2. 已确认 Java / Python / 前端三层分工。
3. 已确认 LangGraph 只负责诊断和推荐，不直接写业务库。
4. 已确认 Java 统一生成 `runCode / stepCode / actionCode`。
5. 已确认 `WAITING_CONFIRMATION` 是 Java run 状态，不要求 LangGraph 在确认后继续执行。
6. 已确认 `agent_run / agent_step / agent_action` 三张表作为轨迹和审计模型。
7. 已确认 v1 不做 MCP、多 Agent、多轮聊天和自动危险操作。

### Day 1：状态模型、工具协议和三张表

1. 已新增 Flyway 迁移 `V18__create_agent_tables.sql`，创建 `agent_run / agent_step / agent_action` 三张表。
2. 已新增 Agent 相关枚举：
   - `AgentRunMode`
   - `AgentRunStatus`
   - `AgentStepType`
   - `AgentStepStatus`
   - `AgentActionRiskLevel`
   - `AgentActionStatus`
   - `AgentToolExecutionMode`
3. 已新增 Agent 持久化实体、Mapper 和 Repository：
   - `AgentRunEntity / AgentRunMapper / AgentRunRepository`
   - `AgentStepEntity / AgentStepMapper / AgentStepRepository`
   - `AgentActionEntity / AgentActionMapper / AgentActionRepository`
4. 已新增 Java 与 Python Agent Runtime 的第一版协议 DTO：
   - `AgentRuntimeRequest`
   - `AgentRuntimeResponse`
   - `AgentRuntimeStepResult`
   - `AgentRuntimeActionDraft`
   - `AgentToolDefinition`
5. 已在 `rag-ai-service` 新增 `app/agent/` 包，并补入 Python 侧 `AgentState` 与 Runtime 请求/响应模型草案。

### Day 2：Agent 查询 API 与 Service 骨架

1. 已新增 `AgentRunCreateRequest`。
2. 已新增 `AgentRunResponse / AgentStepResponse / AgentActionResponse`。
3. 已新增 `AgentRunService`，支持：
   - 创建 Agent run
   - 查询 Agent run 详情
   - 查询详情时组装 steps/actions
   - 校验 run 必须属于当前知识库
4. 已新增 `AgentController`，支持：
   - `POST /api/knowledge-bases/{kbCode}/agent/runs`
   - `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}`
5. 已新增 `AgentRunServiceTest / AgentControllerTest`。
6. Day 2 继续保持不调用 Python Agent Runtime、不实现 confirm/reject、不接前端。

## 当前未实现

1. 还没有封装 P0 只读工具 `system.health.check / kb.readiness.check`。
2. 还没有在 `rag-ai-service` 内引入 LangGraph。
3. 还没有实现 Agent 工具调用节点和状态图。
4. 还没有持久化 Python Runtime 返回的 steps/actions。
5. 还没有实现 confirm/reject。
6. 还没有前端 Agent 工作台。
7. 还没有 Agent 演示场景。

## 当前风险

1. `Java -> Python -> Java` 链路会增加接口契约复杂度，需要坚持“Java 是权威状态中心”的边界。
2. 两周 MVP 范围偏紧，必须优先完成 P0/P1，不要提前扩展 MCP、多 Agent 或完整多轮。
3. `qa.retrieve.probe` 有简历价值，但涉及 Dense / Hybrid 对比 UI 和诊断规则，必要时降级为 P2。
4. 写操作必须 human-in-the-loop，不能为了演示效果绕过确认和白名单。

## 下一步

从 [plan.md](./plan.md) 的 Day 3 开始：

1. Java 封装 `system.health.check` 工具。
2. Java 封装 `kb.readiness.check` 工具。
3. 明确 Agent 工具白名单和只读工具执行结果结构。
4. 保持 Day 3 不接 LangGraph，只先建立 Java 可控工具边界。

## 已验证

1. `mvn -q -pl rag-backend -DskipTests compile` 已通过。
2. `./venv/bin/python -m py_compile rag-ai-service/app/agent/__init__.py rag-ai-service/app/agent/state.py` 已通过。
3. `mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest test` 已通过。

## 恢复入口

下次继续开发时按这个顺序恢复：

1. 先读本文件。
2. 再读 [plan.md](./plan.md)。
3. 如需理解长期设计取舍，读 [RFC-0012](../../rfcs/RFC-0012-langgraph-rag-ops-agent.md)。
