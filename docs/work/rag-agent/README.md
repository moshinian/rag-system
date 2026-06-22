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

当前 Agent 能力已经进入第 3 周“单 Agent 智能 Tool-use”改造：

1. 主计划已确认，保存在 [plan.md](./plan.md)。
2. 长期设计决策已收口到 [RFC-0012](../../rfcs/RFC-0012-langgraph-rag-ops-agent.md)。
3. Day 1 已完成 `AgentState` 草案、工具协议、状态枚举和三张表。
4. Day 2 已完成 Agent run 创建、详情查询、`AgentRunService` 和 `AgentController` 骨架。
5. Day 3 已完成 `system.health.check` 和 `kb.readiness.check` 两个 P0 只读工具。
6. Day 4 已完成 `rag-ai-service` 内的 LangGraph 最小诊断图和 `POST /v1/agent/runs`。
7. Day 5 已完成 Java 调用 Python Agent Runtime，并将返回 steps/actions 落库。
8. Day 6 已完成 `documents.status.scan` 和 `indexing.tasks.scan`。
9. Day 7 已完成 `reembedRequired` 和 `FAILED indexing task` 两个场景的端到端测试固定。
10. Day 8 已完成 `document.indexing_task.retry` 的 confirm/reject API、白名单/风险校验和 Java 侧 retry 执行落库。
11. Day 9 已完成 `embedding.rebuild.submit` 的确认执行，复用现有 `EmbeddingRebuildService.submit(operator)`。
12. Day 10 已完成 `/kb/:kbCode/agent` 前端页面，可创建 run、查询 run、展示 summary/status/steps/actions。
13. Day 11 已完成 Agent 执行轨迹 timeline 和推荐动作卡片展示。
14. Day 12 已完成前端推荐动作 `confirm/reject`，调用 Java API 后刷新 run 并展示执行结果。
15. Day 13 已完成 `qa.retrieve.probe` 简化版，对同一 question 对比 Dense / Hybrid 检索结果并输出诊断 signals。
16. Day 14 已作为第 2 周收口目标，后续表达以第 3 周智能 Tool-use Agent 为主线。
17. Day 15 已完成智能 Agent 基础骨架：
    - 新增 `INTELLIGENT_TOOL_AGENT` runMode。
    - 保留 legacy 固定图 `build_readiness_diagnosis_graph()`。
    - 新增智能图 `build_intelligent_tool_agent_graph()`。
    - Tool Definition 升级为 v2。
    - Java 暴露内部 tool definitions 查询接口。
    - Python Runtime 支持 LLM_DECISION / TOOL_CALL 轨迹、recommended action 强制拦截、fake MCP tool 和只读 CLI tool。
18. Day 18 已完成 LLM 决策校验和失败恢复测试。
19. Day 19 已完成智能模式 recommended action 到 Java `WAITING_CONFIRMATION` 的落库边界测试。
20. Day 20 已完成 fake MCP tool 和只读 CLI tool 的配置化最小 MVP。
21. 已补齐智能模式前端入口：`INTELLIGENT_TOOL_AGENT` runMode 和 `LLM_DECISION` step type。
22. 已完成一次三端联调：通过前端 Vite proxy 创建 `INTELLIGENT_TOOL_AGENT` run，Java 调 Python，Python 在同一主循环中执行 fake MCP tool 和只读 CLI tool，run `AR-327301374603825153` 返回 `SUCCEEDED`。
23. 下一步从 `plan.md` 第 3 周 Day 21 继续：准备确定性演示数据、固定 demo timeline 和面试材料。

## History

1. [work day1.md](./work%20day1.md) 记录 Agent 状态模型、工具协议和三张表的基础落地。
2. [work day2.md](./work%20day2.md) 记录 Agent 查询 API 与 Service 骨架。
3. [work day3.md](./work%20day3.md) 记录 P0 只读工具封装。
4. [work day4.md](./work%20day4.md) 记录 Python LangGraph 最小诊断图。
5. [work day5.md](./work%20day5.md) 记录 Java 调用 Python Runtime 并落库。
6. [work day6.md](./work%20day6.md) 记录 documents/status 与 indexing/tasks 扫描。
7. [work day7.md](./work%20day7.md) 记录两个演示场景端到端验收。
8. [work day8.md](./work%20day8.md) 记录 `document.indexing_task.retry` 确认执行计划。
9. [work day9.md](./work%20day9.md) 记录 `embedding.rebuild.submit` 确认执行计划。
10. [work day10.md](./work%20day10.md) 记录前端 Agent 工作台最小闭环计划。
11. [work day11.md](./work%20day11.md) 记录 Agent timeline 与推荐动作卡片计划。
12. [work day12.md](./work%20day12.md) 记录前端 confirm/reject 与执行结果展示计划。
13. [work day13.md](./work%20day13.md) 记录 `qa.retrieve.probe` Dense / Hybrid 对比计划。
14. [work day15.md](./work%20day15.md) 记录智能 Tool-use Agent 状态模型与计划收口。
15. [work day16.md](./work%20day16.md) 记录 Tool Registry v2 与工具发现接口。
16. [work day17.md](./work%20day17.md) 记录智能 LangGraph 主循环骨架。
17. [work day18.md](./work%20day18.md) 记录 LLM 决策校验和失败恢复。
18. [work day19.md](./work%20day19.md) 记录安全拦截和 recommended action 落库边界。
19. [work day20.md](./work%20day20.md) 记录 MCP/CLI 最小配置化接入。

## Related Boards

1. [rag-backend](../rag-backend/README.md)
2. [rag-ai-service](../rag-ai-service/README.md)
3. [rag-frontend](../rag-frontend/README.md)
4. [RFC Index](../../rfcs/README.md)
