# 第五轮：异常、测试、文档收口

请执行 Agent SSE 改造第五批次。本轮只做异常场景、测试和文档收口，不新增大功能。

## 本轮目标

补齐：

1. 重复 event 测试。
2. Last-Event-ID 补发测试。
3. terminal 缺失测试。
4. Python stream 中断测试。
5. Java 事务回滚不推 SSE 测试。
6. emitter 清理测试。
7. heartbeat/readTimeout 测试。
8. README / Agent 设计文档 / 本地验证命令。
9. 面试表达总结。

## 关键要求

1. 不自动重试整个 Agent run。
2. 数据库不可用时记录结构化错误日志。
3. 文档标注后续优化：

   * recovery scheduler
   * 多实例 Redis Pub/Sub / MQ 广播
   * 更强的 cancellation
   * optional final answer token streaming
4. 不引入 WebFlux。
5. 不让 React 直连 Python。

## 验收命令

请执行或说明无法执行的原因：

./.venv/bin/python -m pytest rag-ai-service/tests
mvn -q -pl rag-backend test
cd rag-frontend && npm run build

同时提供 curl -N 验证 Python SSE 和 Java SSE 的命令。

## 实施结果（2026-06-24）

Round 5 已完成异常场景、测试和文档收口。本轮没有新增大功能，没有引入 WebFlux，没有让 React 直连 Python，也没有增加 Agent run 自动重试。

### 补充测试

新增或补强：

1. `AgentRunExecutorTest`
   - Python stream 正常 EOF 但无 terminal 时标记失败。
   - Python stream 抛异常时标记失败。
   - Java 写库兜底失败时只记录结构化错误日志，不让后台线程继续抛出未处理异常。
2. `AgentRunSseServiceTest`
   - terminal event publish 后关闭 emitter 并清理 run channel。
3. `AgentRunEventSseListenerTransactionTest`
   - 事务 commit 后才推 SSE。
   - 事务 rollback 不推 SSE。
4. `AgentRunEventMigrationScriptTest`
   - 保护 `agent_step(run_code, node_invocation_id) WHERE node_invocation_id IS NOT NULL` partial unique index。
   - 保护 `agent_run_event(run_code, id)` 数据库顺序索引。
5. `test_agent_runtime.py`
   - Python stream 等待阻塞 LLM/tool 时输出 heartbeat comment frame。

已有 Round 1~4 测试继续覆盖：

- 重复 `event_code` 幂等插入。
- Last-Event-ID 按数据库 id 补发。
- runtime terminal 规范化。
- pending action 场景只产生 `RUN_WAITING_CONFIRMATION`。
- nodeInvocationId 精确关联循环节点。
- client disconnect 后 Python 协作式取消。
- streaming read timeout 不低于 60 秒。

### 文档收口

已更新：

- `README.md`
  - 增加 Java `/events` 接口。
  - 增加 Python `/v1/agent/runs/stream` 接口。
  - 明确 React 只连 Java、Java 是状态权威、SSE 只是实时通知。
  - 标注后续优化边界。
- `docs/rfcs/RFC-0012-langgraph-rag-ops-agent.md`
  - 状态从 Planned 更新为 Implemented。
  - 补充 SSE 流式架构、双层事件、Java terminal 规范化规则。
  - 补充 Non-Goals 和 Follow-ups。
- `docs/work/rag-sse/plan.md`
  - Round 5 标记完成。

### 后续优化明确标注

1. `recovery scheduler`：扫描长时间 `RUNNING` 且无新事件的孤儿 run，标记 `FAILED`。
2. 多实例广播：SSE 订阅当前是单 Java 实例内存 channel，多实例部署需要 Redis Pub/Sub 或 MQ。
3. 更强 cancellation：当前 Python 只在 node 边界协作停止，不强杀阻塞中的 LLM/tool 调用。
4. optional final answer token streaming：当前阶段只做 step event streaming，不做 LLM token streaming。

### 本地验证命令

Python 测试：

```bash
./.venv/bin/python -m pytest rag-ai-service/tests
```

Java 测试：

```bash
mvn -q -pl rag-backend test
```

前端构建：

```bash
cd rag-frontend
npm run build
```

Python SSE curl：

```bash
curl --noproxy '*' -N -X POST http://127.0.0.1:8001/v1/agent/runs/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"runCode":"AR-round5-python","kbCode":"day20-cn-kb","goal":"诊断当前知识库问答就绪状态","question":"当前知识库是否可以正常问答？","runMode":"INTELLIGENT_TOOL_AGENT"}'
```

Java 创建 run：

```bash
curl --noproxy '*' -sS -X POST http://127.0.0.1:8080/api/knowledge-bases/day20-cn-kb/agent/runs \
  -H 'Content-Type: application/json' \
  -d '{"goal":"诊断当前知识库问答就绪状态并给出结构化结论","question":"当前知识库是否可以正常问答？","runMode":"INTELLIGENT_TOOL_AGENT","createdBy":"round5"}'
```

Java SSE curl：

```bash
curl --noproxy '*' -N http://127.0.0.1:8080/api/knowledge-bases/day20-cn-kb/agent/runs/{runCode}/events
```

Last-Event-ID 补发验证：

```bash
curl --noproxy '*' -N \
  -H 'Last-Event-ID: {eventId}' \
  http://127.0.0.1:8080/api/knowledge-bases/day20-cn-kb/agent/runs/{runCode}/events
```

### 本轮实际验证结果

已执行并通过：

```bash
./.venv/bin/python -m pytest rag-ai-service/tests
# 43 passed, 1 skipped

mvn -q -pl rag-backend test
# passed

cd rag-frontend
npm run build
# passed
```

已按 `.vscode/launch.json` 注入本地环境变量启动 Python 和 Java，并完成真实 SSE 联调：

1. Python health：`GET http://127.0.0.1:8001/health` 返回 `UP`。
2. Java health：`GET http://127.0.0.1:8080/api/health` 返回 `UP`。
3. Python SSE：
   - `POST /v1/agent/runs/stream`
   - runCode：`AR-round5-python`
   - 返回 35 条 SSE event
   - 终态：`RUN_COMPLETED`
4. Java SSE：
   - 创建 run：`AR-328194253106843649`
   - `POST /api/knowledge-bases/day20-cn-kb/agent/runs` 快速返回 `RUNNING`
   - `GET /api/knowledge-bases/day20-cn-kb/agent/runs/AR-328194253106843649/events` 返回 35 条事件
   - 终态：`RUN_COMPLETED`
5. 数据库验证：
   - `agent_run_event`：35 条
   - terminal event：1 条
   - `agent_step`：9 条
   - distinct `node_invocation_id`：9 个
   - `node_invocation_id IS NULL` 的 step：0 条
   - run 最终状态：`SUCCEEDED`
6. Last-Event-ID 验证：
   - 使用 `Last-Event-ID: AR-328194253106843649-000033`
   - 只补发 `000034` 和 `000035`
7. 日志验证：
   - 可见 `agent.runtime.stream.started`
   - 可见逐条 `agent.runtime.stream.event.received`
   - 可见 `agent.runtime.stream.completed`

验证完成后已停止 Java / Python 临时服务，并确认 8001 / 8080 / 5173 端口无监听。

### 面试表达总结

这次改造把 Agent run 从同步阻塞调用升级成异步事件驱动模型。前端创建 run 后，Java 立即创建 `agent_run` 并返回 `runCode`，后台线程再调用 Python `/v1/agent/runs/stream` 执行 LangGraph。Python 在 node 层产出标准化 Runtime event，Java 边消费边事务化写入 `agent_run_event / agent_step / agent_action / agent_run`，并且只在事务提交后通过 `SseEmitter` 推送给 React。

架构边界上，Python 只负责 Agent Runtime 执行和事件产出；Java 负责状态权威、持久化、安全策略、action catalog 和 human-in-the-loop；React 只连接 Java SSE。即使浏览器 SSE 断开，后台执行和数据库落库也不受影响；重连后可以通过数据库事件按 Last-Event-ID 补发恢复。这解决了同步 timeout、执行过程不可观测和刷新后不可回放的问题，同时保留了旧 `/v1/agent/runs` JSON 接口作为兼容路径。
