# 第三轮：Java 接 Python streaming + event applier

第三轮才把 Java 后台从旧 JSON client 切到 Python SSE client。

请执行 Agent SSE 改造第三批次。本轮目标是让 Java 后台消费 Python /v1/agent/runs/stream，并用 streaming event applier 更新数据库和推送 React SSE。

## 本轮目标

新增或修改：

1. AgentRuntimeStreamingClient。
2. SseEventParser。
3. AgentRunEventApplier。
4. AgentRunExecutor 从旧 AgentRuntimeClient.run() 切换到 streaming client。
5. 根据 Python event 更新 agent_run_event / agent_step / agent_action / agent_run。
6. 保留旧 AgentRuntimeClient 作为兼容代码，不删除。

## 允许修改范围

允许修改：

* rag-backend Agent Runtime 集成代码
* AgentRunExecutor
* AgentRunEventService / Applier
* RagAiGatewayProperties
* Java 单元测试

不允许修改：

* React 前端
* Python streaming 协议，除非发现必须修复的小 bug
* action confirm/reject 行为
* Spring MVC 改成 WebFlux

## 关键要求

1. AgentRuntimeStreamingClient 调用 POST /v1/agent/runs/stream：

   * Accept: text/event-stream
   * Content-Type: application/json; charset=UTF-8
   * 透传 requestId
2. streaming read timeout 默认 120 秒，且不得低于 60 秒。
3. SseEventParser 支持：

   * id
   * event
   * 多行 data
   * 空行提交事件
   * comment heartbeat
   * EOF flush
4. 校验 SSE event 名称与 JSON type 一致。
5. agent_run_event 插入使用 ON CONFLICT DO NOTHING。
6. 只有成功插入新 event_code 时才应用领域状态，重复 event 不重复创建 action 或更新 step。
7. STEP_STARTED：

   * 按 runCode + nodeInvocationId 创建 RUNNING step。
8. STEP_COMPLETED / STEP_FAILED：

   * 按 runCode + nodeInvocationId 精确更新 step。
9. ACTION_RECOMMENDED：

   * 必须通过 RecommendedActionCatalog。
   * Java 生成 actionCode。
   * 以 catalog 风险和确认规则为准。
   * 同一个 event_code 不得重复创建 action。
10. terminal 应用：

    * Python RUN_COMPLETED 且没有待确认 action -> Java run SUCCEEDED，对前端发布 RUN_COMPLETED。
    * 存在待确认 action -> Java run WAITING_CONFIRMATION，对前端发布 RUN_WAITING_CONFIRMATION。
    * Python RUN_FAILED -> Java run FAILED。
11. 明确区分 Python runtime event 和 Java frontend event。

    * Java 对前端发布的是规范化后的事件。
    * 如果 Python RUN_COMPLETED 但 Java 判断需要 WAITING_CONFIRMATION，前端只能看到 RUN_WAITING_CONFIRMATION，不要先看到 RUN_COMPLETED。
    * agent_run_event 表保存 Java 规范化后的 event_type，payload_json 可保留 pythonEventType。
12. Python stream 正常 EOF 但没有 terminal、解析失败、应用失败或连接异常时，Java 创建合成 RUN_FAILED 并标记 run 失败。
13. 事务提交后才能推 SSE，继续使用 afterCommit 机制。

## 验收标准

1. Java 创建 run 后，后台能消费 Python SSE。
2. agent_run_event 按事件逐条落库。
3. agent_step 能按 nodeInvocationId 精确创建和更新。
4. action recommended 能创建 pending action，并使 run 进入 WAITING_CONFIRMATION。
5. Python stream 断开且无 terminal 时，Java run 进入 FAILED。
6. Java SSE /events 能实时看到从 Python streaming 转发来的事件。
7. mvn -q -pl rag-backend test 通过，或者说明不能通过的具体原因。

## 完成后请停止

不要改 React Timeline UI。只要 Java SSE 能用 curl -N 验证即可。

---

## 实施结果（2026-06-24）

Round 3 已完成，Java 后台执行链路已从旧 JSON runtime client 切换为 Python SSE streaming client。旧 `AgentRuntimeClient` 和 Python `/v1/agent/runs` 普通 JSON 接口继续保留。

### 已实现

1. 新增 `AgentRuntimeStreamingClient`：

   * 调用 `POST /v1/agent/runs/stream`。
   * 设置 `Accept: text/event-stream` 和 JSON Content-Type。
   * 透传 MDC 中的 requestId。
   * streaming read timeout 默认 120 秒，并强制不低于 60 秒。
   * 校验 SSE `id`、`event` 与 JSON runtime event 内容的一致性。

2. 新增 `SseEventParser`：

   * 支持 `id:`、`event:`、多行 `data:`、空行提交、comment heartbeat 和 EOF flush。

3. 新增 `AgentRunEventApplier`：

   * 明确区分 Python `AgentRuntimeEvent` 和 Java `AgentRunEvent`。
   * 先完成 Java 终态判断，再持久化唯一的前端 terminal event。
   * `payload_json` 保留 `pythonEventType`、runtime payload 和 runtime createdAt。
   * 只有 `event_code` 首次成功插入后才应用 step/action/run 状态。
   * `STEP_STARTED` 创建带 `nodeInvocationId` 的 RUNNING step。
   * `STEP_COMPLETED` / `STEP_FAILED` 使用 `runCode + nodeInvocationId` 精确更新。
   * `ACTION_RECOMMENDED` 通过 `RecommendedActionCatalog` 校验，由 Java 生成 actionCode，并以 Java catalog 的风险和确认规则为准。
   * Python `RUN_COMPLETED` 在存在 pending action 时只转换为 `RUN_WAITING_CONFIRMATION`，不会先发布 `RUN_COMPLETED`。

4. `AgentRunExecutor` 已切换到 streaming client：

   * 边接收、边事务落库、边在事务提交后通过 SSE 发布。
   * 正常 EOF 无 terminal、解析失败、事件应用失败或连接异常时，创建合成 `RUN_FAILED`。
   * React SSE 断开不影响后台执行。

5. 创建 run 时不再额外生成 compatibility `RUN_STARTED`，避免与 Python streaming 的 `RUN_STARTED` 重复。

### 测试结果

以下命令通过：

```bash
mvn -q -pl rag-backend \
  -Dtest=SseEventParserTest,AgentRuntimeStreamingClientTest,AgentRunEventApplierTest,AgentRunExecutorTest,AgentRunServiceTest,AgentRunEventServiceTest,AgentRunSseServiceTest,AgentControllerTest \
  test

mvn -q -pl rag-backend test
```

覆盖内容包括：

* SSE comment、多行 data、EOF flush。
* event name 与 JSON type 一致性校验。
* read timeout 最低 60 秒。
* step correlation 精确更新。
* action event 幂等。
* Java catalog 安全规则覆盖 Python 建议值。
* pending action 场景只生成 `RUN_WAITING_CONFIRMATION`。
* stream 无 terminal 时合成失败。

### 真实联调结果

按 `.vscode/launch.json` 注入运行参数，启动 Java 和 Python 后完成真实联调：

```text
runCode: AR-328186891147022337
Java final status: SUCCEEDED
agent_run_event: 35 条
terminal event: 1 条 RUN_COMPLETED
agent_step: 9 条
不同 nodeInvocationId: 9 个
nodeInvocationId 为空的 step: 0 条
```

`Last-Event-ID: AR-328186891147022337-000033` 重连后只补发 `000034` 和 `000035`，补发顺序使用数据库 id。

Java 日志已确认：

```text
agent.runtime.stream.started
agent.runtime.stream.event.received
agent.runtime.stream.completed
```

### 后续项

* Round 4 再实现 React Timeline UI，本轮未修改前端。
* Java 数据库整体不可用时，本期只能记录失败持久化日志；后续可增加 recovery scheduler，扫描长时间处于 RUNNING 的孤儿 run 并标记 FAILED。
