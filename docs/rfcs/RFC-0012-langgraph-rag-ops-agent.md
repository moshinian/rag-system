# RFC-0012 LangGraph RAG Ops Agent

- Status: Implemented
- Created: 2026-06-16
- Last Updated: 2026-06-24
- Owners: RAG Team

## Summary

本 RFC 记录系统从“企业知识库 RAG 工作台”继续演进到“基于 LangGraph 的 RAG 运维诊断 Agent”的设计边界。2026-06-24 后，Agent run 已进一步从同步阻塞调用改造成异步 SSE 流式执行模型。

核心结论是：

1. Agent v1 主场景固定为 RAG 运维诊断，而不是泛聊天或万能任务执行。
2. Java 后端继续作为业务权威和 Agent Run 状态中心。
3. Python `rag-ai-service` 新增 LangGraph Agent Runtime，只负责状态图、工具编排、诊断推理和报告生成。
4. 前端新增 Agent 工作台，展示执行轨迹、推荐动作和 human-in-the-loop 确认。
5. 写操作必须通过 Java 后端白名单和用户确认执行，LangGraph 与 LLM 都不能绕过该边界。
6. Java 是 Agent run 的状态权威；Python 只产出 Runtime event，Java 规范化后落库并推送前端。

## Context

当前系统已经具备第一版企业知识库 RAG 主链路：

1. 知识库、文档、chunk 和问答记录管理。
2. 文档上传、异步索引、失败重试和卡住任务恢复。
3. `qa/readiness`、`qa/retrieve`、`qa/ask` 和 `qa/history`。
4. Dense / Hybrid 检索、RRF fusion、最小观测字段和评测基线。
5. `rag-ai-service` 作为 Java 前面的 AI Gateway，统一承接 embeddings 和 chat completions。
6. 前端工作台已经覆盖知识库、文档、检索、问答、历史和健康页。

这些能力已经让项目具备完整 RAG 工程闭环，但还没有显式 Agent Runtime，也没有把系统已有运维动作组织成“目标理解 -> 工具调用 -> 结果观察 -> 诊断归因 -> 推荐修复 -> 人工确认执行”的 Agent 闭环。

为了让下一阶段既能服务真实业务，又能支撑 AI 应用岗位对 Agent 经验的要求，系统需要在现有 RAG 主链路之上新增一层受控 Agent 编排能力。

## Decision

系统下一阶段采用“LangGraph Agent Runtime + Java 受控工具执行 + 前端轨迹展示”的架构。

### 1. Java 是业务权威

Java 后端负责：

1. 创建和管理 `agent_run`。
2. 生成 `runCode / stepCode / actionCode`。
3. 持久化 `agent_step` 和 `agent_action`。
4. 暴露受控工具能力。
5. 校验工具白名单、风险等级和确认状态。
6. 执行确认后的写操作。

这意味着 Python Agent Runtime 不直接写业务库，也不生成最终业务主键。

### 2. Python 是 Agent Runtime

`rag-ai-service` 新增 `app/agent/` 模块，负责：

1. 引入 LangGraph。
2. 定义 `AgentState`。
3. 实现固定诊断状态图。
4. 调用 Java 提供的只读工具。
5. 返回诊断步骤、诊断结论和推荐动作草案。

Python 可以辅助生成自然语言诊断说明，但不能决定权限，也不能绕过 Java 执行写操作。

### 3. Human-in-the-loop 是 v1 强约束

Agent v1 的写操作必须走确认：

1. Agent 只能生成推荐动作。
2. Java 根据推荐动作落库为 `agent_action`。
3. 前端展示风险等级、原因和确认按钮。
4. 用户确认后，Java 执行白名单动作。
5. 执行结果写回 `agent_action`。

`WAITING_CONFIRMATION` 是 Java run 状态，不要求 LangGraph 在确认后继续执行。

### 4. v1 聚焦两个演示场景

第一版优先完成：

1. `reembedRequired`：readiness 异常后推荐全量重嵌入。
2. `FAILED indexing task`：发现失败索引任务后推荐重试。

可选增强是 `qa.retrieve.probe`，用 Dense / Hybrid 对比做检索质量诊断。

## Proposed Model

LangGraph v1 状态图固定为：

```text
START
  ↓
parse_goal
  ↓
system_health_check
  ↓
kb_readiness_check
  ↓
documents_status_scan
  ↓
indexing_tasks_scan
  ↓
should_run_retrieve_probe?
      ├── yes → qa_retrieve_probe
      └── no
  ↓
diagnose
  ↓
recommend_actions
  ↓
generate_report
  ↓
END
```

Java 根据 Python 返回结果决定最终 run 状态：

1. 有待确认动作：`WAITING_CONFIRMATION`
2. 无待确认动作且执行成功：`SUCCEEDED`
3. Agent Runtime 或工具调用失败：`FAILED`

当前 SSE 流式模型为：

```text
React POST /api/knowledge-bases/{kbCode}/agent/runs
  -> Java 创建 RUNNING run 并提交后台任务，立即返回 runCode
  -> React GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/events
  -> Java 补发 agent_run_event 历史事件并保持 SseEmitter

Java 后台线程
  -> POST Python /v1/agent/runs/stream
  -> Python 执行 LangGraph 并发送 Runtime event
  -> Java 事务化写 agent_run_event / agent_step / agent_action / agent_run
  -> AFTER_COMMIT 后推送前端 SSE
```

事件分两层：

1. `AgentRuntimeEvent`：Python 到 Java 的内部运行事件。
2. `AgentRunEvent`：Java 规范化后落库并推送 React 的前端事件。

如果 Python 发送 `RUN_COMPLETED`，Java 会先检查是否存在 `PENDING_CONFIRMATION` action；有待确认动作时，前端只接收 `RUN_WAITING_CONFIRMATION`，不会先接收 `RUN_COMPLETED`。

## Implementation

执行计划维护在 [rag-agent plan](../work/rag-agent/plan.md)。

### 1. Persistence

新增三张表：

1. `agent_run`
2. `agent_step`
3. `agent_action`

它们分别承担：

1. 一次 Agent 诊断任务。
2. LangGraph 节点和工具调用轨迹。
3. 推荐动作、确认执行和审计结果。

### 2. API Contract

Java 对前端提供：

```text
POST /api/knowledge-bases/{kbCode}/agent/runs
GET  /api/knowledge-bases/{kbCode}/agent/runs/{runCode}
GET  /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/events
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm
POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject
```

Python Agent Runtime 提供：

```text
POST /v1/agent/runs
POST /v1/agent/runs/stream
```

该接口由 Java 调用。Python 返回步骤和推荐动作草案，不返回最终业务主键。

`/v1/agent/runs` 继续保留为普通 JSON 兼容接口；`/v1/agent/runs/stream` 返回 `text/event-stream`，只供 Java 内部消费，浏览器不直接连接 Python。

### 3. Tool Boundary

P0 只读工具：

1. `system.health.check`
2. `kb.readiness.check`

P1 工具：

1. `documents.status.scan`
2. `indexing.tasks.scan`
3. `document.indexing_task.retry`

P2 工具：

1. `qa.retrieve.probe`
2. `embedding.rebuild.submit`

v1 暂缓：

1. `qa.ask.probe`
2. `kb.enable.with_failed_task_retry`
3. `redis.probe.run`
4. MCP
5. 多 Agent 协作
6. 多轮聊天
7. 自动危险操作

### 4. Risk Policy

Agent action 使用三档风险：

1. `LOW`：允许确认执行。
2. `MEDIUM`：必须确认执行。
3. `HIGH`：v1 禁止执行，只能展示建议。

## Consequences

正面影响：

1. 项目从 RAG 问答系统升级为带 Agent 编排能力的 AI 应用系统。
2. LangGraph 的状态图、工具调用和 human-in-the-loop 让 Agent 能力更显性。
3. Java 后端继续保持业务安全边界，不让 LLM 或 Python Runtime 直接操作业务库。
4. `agent_run / agent_step / agent_action` 让过程可追踪、可审计、可演示。
5. Dense / Hybrid 检索探测可以复用现有 Week 4 成果，形成 RAG 质量诊断亮点。

代价与约束：

1. `Java -> Python -> Java` 链路会提高接口契约复杂度。
2. 需要严格避免把 Agent 做成另一个泛聊天入口。
3. 两周 MVP 需要控制范围，优先完成 P0/P1 和两个演示场景。
4. 不能把计划能力提前写进根 README 的已完成能力列表。

## Non-Goals

Agent v1 不做：

1. 通用聊天 Agent。
2. 多 Agent 协作。
3. MCP。
4. 自动危险操作。
5. 复杂权限系统。
6. 确认后回到 LangGraph 继续执行。
7. 跨知识库长期记忆。
8. 完整多轮会话产品。
9. WebFlux 全链路响应式迁移。
10. 让 React 直连 Python。
11. LLM final answer token streaming。

## Follow-ups

1. 增加 recovery scheduler：扫描长时间 `RUNNING` 且无新事件的孤儿 run，标记为 `FAILED`。
2. 多实例部署时引入 Redis Pub/Sub 或 MQ，把 afterCommit 事件广播到持有不同 SSE 连接的 Java 实例。
3. 强化 Python cancellation：当前只在 node 边界协作式停止，不强杀正在阻塞的 LLM/tool 调用。
4. 如果未来需要更强交互体验，可在 step 级 streaming 稳定后评估 optional final answer token streaming。

## Open Questions

1. `qa.retrieve.probe` 是否在第一轮 MVP 内完成，还是作为 P2 延后。
2. Python Agent Runtime 是否需要持久化 LangGraph checkpoint，还是 v1 只依赖 Java 落库。
3. Agent 诊断报告是否第一版就调用 LLM 润色，还是先用规则模板输出。
4. Agent 工具接口是复用现有 Java API，还是新增内部专用接口。

## References

1. [README.md](../../README.md)
2. [Work Index](../work/README.md)
3. [rag-agent plan](../work/rag-agent/plan.md)
4. [rag-agent current status](../work/rag-agent/current-status.md)
5. [rag-backend current status](../work/rag-backend/current-status.md)
6. [rag-ai-service current status](../work/rag-ai-service/current-status.md)
7. [rag-frontend current status](../work/rag-frontend/current-status.md)
8. [RFC-0011](./RFC-0011-session-reuse-and-multi-turn-conversation-model.md)
