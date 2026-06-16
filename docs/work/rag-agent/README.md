# rag-agent Work

## Position

`rag-agent` 是当前 RAG 系统的下一阶段改造板块，目标是在现有企业知识库 RAG 主链路之上补入一层可演示、可审计、可写进简历的 LangGraph 运维诊断 Agent。

这个板块的定位是：

1. 把现有 `health / readiness / documents / indexing tasks / retrieval` 能力封装成 Agent 可调用工具。
2. 用 LangGraph 表达 Agent 状态图和工具调用编排。
3. 让 Java 后端继续承担业务权威、安全边界、落库审计和确认执行。
4. 让前端承接 Agent 轨迹、推荐动作和 human-in-the-loop 操作。

## Read Here First

后续恢复 Agent 开发时，固定按下面顺序阅读：

1. [current-status.md](./current-status.md)
2. [plan.md](./plan.md)
3. [RFC-0012](../../rfcs/RFC-0012-langgraph-rag-ops-agent.md)

## Current Breakpoint

当前 Agent 能力还没有进入实现阶段。已经完成的是路线收口和文档化：

1. 主计划已确认，保存在 [plan.md](./plan.md)。
2. 长期设计决策已收口到 [RFC-0012](../../rfcs/RFC-0012-langgraph-rag-ops-agent.md)。
3. 下一步从 `plan.md` 的 Day 1 开始：确定 `AgentState`、工具协议、状态枚举和三张表。

## Related Boards

1. [rag-backend](../rag-backend/README.md)
2. [rag-ai-service](../rag-ai-service/README.md)
3. [rag-frontend](../rag-frontend/README.md)
4. [RFC Index](../../rfcs/README.md)
