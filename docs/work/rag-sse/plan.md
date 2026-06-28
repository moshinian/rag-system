# Agent SSE 流式改造实施计划（最终版）

## 当前进度

- Round 1：已完成（2026-06-24）
- Round 2：已完成（2026-06-24）
- Round 3：已完成（2026-06-24）
- Round 4：已完成（2026-06-24）
- Round 5：已完成（2026-06-24）
- Round 6：已完成（2026-06-24）

## 1. 双层事件协议

明确拆分两套类型：

- AgentRuntimeEvent：Python → Java 的内部运行时事件。
- AgentRunEvent：Java规范化后落库并推送 React 的前端事件。

Java收到 Python terminal 后先计算最终业务状态，再只落一个规范化事件：

- Python RUN_COMPLETED + 无待确认 action → RUN_COMPLETED
- Python RUN_COMPLETED + 有待确认 action → RUN_WAITING_CONFIRMATION
- Python RUN_FAILED → RUN_FAILED

禁止先向前端发布 RUN_COMPLETED，再发布 RUN_WAITING_CONFIRMATION。

agent_run_event.event_type 保存 Java规范化类型；payload_json 保留：

{
"pythonEventType": "RUN_COMPLETED",
"runtimePayload": {}
}

Java分别定义 AgentRuntimeEventType 和 AgentRunEventType，避免混用。

## 2. 数据库迁移

实现前重新扫描迁移目录，使用最新版本后的下一个编号。Round 1 使用 V19 创建 agent_run_event；Round 6 使用 V20 增加 agent_run.runtime_heartbeat_at。

新增 agent_run_event，数据库 id 是唯一补发和展示顺序依据，并为 agent_step 增加可空 node_invocation_id。

索引包括：

CREATE UNIQUE INDEX ...
ON agent_step(run_code, node_invocation_id)
WHERE node_invocation_id IS NOT NULL;

以及：

agent_run_event(event_code) UNIQUE
agent_run_event(run_code, id)

Last-Event-ID 先解析为事件记录，再按同一 run 的数据库 id 继续查询。

## 3. Java异步执行与兼容阶段

- createRun 创建 RUNNING run 后提交 agentExecutor，立即返回。
- 独立线程池默认 2/4/100，线程前缀 rag-agent-，复制 MDC。
- 第一阶段后台仍调用旧 /v1/agent/runs。
- 旧 JSON 结果继续使用现有代码创建 step/action/run 状态。
- compatibility converter 仅根据已落库结果创建前端事件，不调用 streaming event applier。
- compatibility terminal 严格依据 Java最终状态：
  - SUCCEEDED → RUN_COMPLETED
  - FAILED → RUN_FAILED
  - WAITING_CONFIRMATION → RUN_WAITING_CONFIRMATION

## 4. Java事务与 SSE 发布

streaming event applier 在单个事务中完成：

1. 幂等插入规范化事件。
2. 更新 step/action/run。
3. 发布 Spring内部 committed-event。

SSE listener 使用：

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)

禁止事务提交前直接调用 sseService.publish()。

重复 event_code 使用数据库冲突忽略；只有首次插入成功才允许创建 action 或更新领域状态。

## 5. Java SSE 订阅并发控制

AgentRunSseService 为每个 run 维护同步锁。订阅流程在同一锁内完成：

1. 查询 Last-Event-ID 后的历史。
2. 注册 emitter。
3. 补发历史。
4. 若历史包含 terminal，则关闭并移除 emitter。

实时 publish 也获取相同 run 锁，避免历史查询和 emitter 注册之间漏事件。

每个订阅保存 lastDeliveredDatabaseId。即使事务已经提交、历史查询已看到事件，而 afterCommit listener 随后再次 publish，也不会重复发送同一数据库事件。

emitter 异常、超时或完成时清理订阅；React断线不影响后台执行。

## 6. Python node correlation

每次 node 执行生成唯一 nodeInvocationId。同一次调用产生的 step、planner、tool 和 observation 事件共享该值。

Java使用：

runCode + nodeInvocationId

精确创建和更新 AgentStepEntity，正确支持循环图中多次执行 llm_plan 和 execute_readonly_tool。

## 7. Python streaming 与取消机制

新增 event sink、traced node、SSE formatter 和 /v1/agent/runs/stream。

stream_sse() 维护：

- terminal_emitted
- threading.Event cancellation flag
- 无界事件 Queue

规则：

- final/fail node 不直接发送 terminal。
- graph 正常结束后统一发送一次 terminal。
- 异常时仅在尚未发送 terminal 时发送 RUN_FAILED。
- 正常消费的 stream 必须恰好一个 terminal。

generator 在关闭或 Java断连时于 finally 设置 cancellation flag。随后：

- sink停止接受和排队新事件。
- traced node 在每个后续 node 开始前检查 flag，并抛出内部取消异常终止 graph。
- 当前正在阻塞的 LLM/tool 调用不强行中断；调用返回后不再 emit，并在下一检查点退出。
- 客户端已经断开时不再尝试发送 terminal。

Python每 10 秒输出 SSE comment heartbeat；Java streaming read timeout 默认 120 秒且不得低于 60 秒。

## 8. Streaming 状态应用

- STEP_STARTED：按 correlation ID 创建 RUNNING step。
- STEP_COMPLETED/FAILED：精确更新对应 step。
- ACTION_RECOMMENDED：事件首次插入后，通过 RecommendedActionCatalog 校验并幂等创建 action。
- Python terminal 先由 Java检查 pending action，再转换为唯一前端 terminal 和最终 run 状态。
- EOF 无 terminal、解析失败或连接异常时，Java创建合成 RUN_FAILED。

## 9. React

- 创建 run 后立即通过 EventSource 连接 Java。
- terminal 后关闭连接并重新查询 run detail，以数据库中的正式 steps/actions 为最终结果。
- 支持自动重连和 Last-Event-ID 历史补发。

## 10. 测试与文档

重点增加：

- 循环 node correlation 测试。
- runtime terminal 规范化测试。
- pending action 场景只产生 RUN_WAITING_CONFIRMATION。
- partial unique index 验证。
- action/event 幂等测试。
- afterCommit 与事务回滚测试。
- SSE订阅注册竞态测试。
- client disconnect 后 Python协作取消测试。
- compatibility converter terminal 映射测试。
- heartbeat/read timeout 测试。
- 三端真实 curl -N 联调。

Round 6 已补充 recovery scheduler，扫描长时间 RUNNING 且 runtime heartbeat 与业务事件都超时的孤儿 run，并标记 FAILED。

所有新增 Java、Python和关键前端逻辑补充中文注释。旧 /v1/agent/runs、现有 confirm/reject API 和 MCP 边界全部保留。

## 11. Round 6 稳定性收口

### 11.1 前端 202 兼容

Java `POST /api/knowledge-bases/{kbCode}/agent/runs` 返回 `202 Accepted`，表示 run 已创建并进入后台执行。

前端 `apiClient` 使用 `response.ok` 判断 HTTP 成功，因此接受所有 2xx 状态，包括 202。创建成功后仍从响应体读取 `runCode`，并建立：

GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/events

### 11.2 持久化 Runtime heartbeat

`agent_run` 增加：

runtime_heartbeat_at TIMESTAMPTZ

含义：Java 最近一次确认 Python Runtime stream 仍活跃的时间。

规则：

- 创建 RUNNING run 时初始化 runtime_heartbeat_at。
- Python SSE comment `: heartbeat` 不进入 agent_run_event，不推送 React，只用于更新 runtime_heartbeat_at。
- Java 对 heartbeat 更新做轻量节流，默认 30 秒。
- 普通 Runtime event 成功消费时也会 touch heartbeat。
- heartbeat 更新失败只记录结构化日志，不中断 stream。
- run 进入 terminal 或后台任务结束后清理内存节流记录。

V20 migration 会对迁移时已有 `status = RUNNING AND runtime_heartbeat_at IS NULL` 的 run 回填 `now()`，给存量 RUNNING run 一个宽限期；已终态 run 保持 null。

### 11.3 Recovery Scheduler

Recovery 默认启用：

rag.agent.recovery.enabled=true

默认配置：

- scan-interval-seconds: 60
- running-timeout-minutes: 10
- idle-timeout-minutes: 3
- heartbeat-update-interval-seconds: 30

开发调试时如果需要长时间挂起 Agent run，可以临时设置：

rag.agent.recovery.enabled=false

或调大 `running-timeout-minutes / idle-timeout-minutes`。

Recovery 判断条件：

- run.status = RUNNING
- run.created_at 早于 running cutoff
- 没有 terminal agent_run_event
- runtime_heartbeat_at 为空或早于 idle cutoff
- idle cutoff 之后没有新的 agent_run_event

满足条件后，Java 在同一事务内：

1. 条件更新 run.status = FAILED。
2. 写入 Java 合成 RUN_FAILED terminal event。

如果 event persist 失败，事务回滚 run 状态更新，避免出现 run 已 FAILED 但没有 terminal event。

Recovery 不自动重试整个 Agent run。自动重试需要单独设计 checkpoint、tool call 幂等和 resume/retry 语义。

### 11.4 并发与迟到事件

Recovery 条件更新会再次检查 heartbeat stale、recent business event 和 terminal event，避免 scan 后刚收到 heartbeat 或业务事件的 run 被误杀。

如果 Recovery 已将 run 标记 FAILED，而 Python 迟到发送 RUN_COMPLETED，`AgentRunEventApplier` 会在落库前检查 Java run 状态并忽略该 runtime event，同时输出结构化日志：

- runCode
- eventCode
- runtimeEventType
- currentRunStatus

### 11.5 多实例边界

当前 Java → React SSE registry 仍是单 Java 实例内存态。多实例部署后，afterCommit event 需要通过 Redis Pub/Sub 或 MQ 广播到持有对应 SSE 连接的实例。
