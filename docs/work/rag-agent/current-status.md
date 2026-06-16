# 当前状态

## 当前结论

`rag-agent` 目前处于正式计划已确认、代码尚未实现的阶段。

当前已经明确的方向是：

**基于 LangGraph 的 RAG 运维诊断 Agent。**

它不是替代现有 RAG 主链路，也不是新增一个泛聊天机器人，而是在已有 RAG 系统上方增加 Agent 编排层：

1. Java 后端继续作为业务权威和 Agent Run 状态中心。
2. Python `rag-ai-service` 新增 LangGraph Agent Runtime。
3. 前端新增 Agent 工作台，展示诊断轨迹、推荐动作和确认执行。
4. v1 聚焦两个可演示业务场景：`reembedRequired` 和 `FAILED indexing task`。

## 已完成

当前已完成的是计划和边界收口：

1. 已确认 Agent 主场景是“RAG 运维诊断 Agent”。
2. 已确认 Java / Python / 前端三层分工。
3. 已确认 LangGraph 只负责诊断和推荐，不直接写业务库。
4. 已确认 Java 统一生成 `runCode / stepCode / actionCode`。
5. 已确认 `WAITING_CONFIRMATION` 是 Java run 状态，不要求 LangGraph 在确认后继续执行。
6. 已确认 `agent_run / agent_step / agent_action` 三张表作为轨迹和审计模型。
7. 已确认 v1 不做 MCP、多 Agent、多轮聊天和自动危险操作。

## 当前未实现

1. 还没有新增 Agent 数据表和 Java API。
2. 还没有在 `rag-ai-service` 内引入 LangGraph。
3. 还没有实现 Agent 工具协议和状态图。
4. 还没有前端 Agent 工作台。
5. 还没有 Agent 演示场景和测试用例。

## 当前风险

1. `Java -> Python -> Java` 链路会增加接口契约复杂度，需要坚持“Java 是权威状态中心”的边界。
2. 两周 MVP 范围偏紧，必须优先完成 P0/P1，不要提前扩展 MCP、多 Agent 或完整多轮。
3. `qa.retrieve.probe` 有简历价值，但涉及 Dense / Hybrid 对比 UI 和诊断规则，必要时降级为 P2。
4. 写操作必须 human-in-the-loop，不能为了演示效果绕过确认和白名单。

## 下一步

从 [plan.md](./plan.md) 的 Day 1 开始：

1. 确定 `AgentState`。
2. 确定 Java 与 Python 之间的工具协议。
3. 确定 Agent 状态枚举。
4. 设计 `agent_run / agent_step / agent_action` 三张表。

## 恢复入口

下次继续开发时按这个顺序恢复：

1. 先读本文件。
2. 再读 [plan.md](./plan.md)。
3. 如需理解长期设计取舍，读 [RFC-0012](../../rfcs/RFC-0012-langgraph-rag-ops-agent.md)。
