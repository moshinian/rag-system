# 第一轮：只做 Java 异步化 + 事件表 + Java SSE 骨架

这一轮不要接 Python streaming，先把 Java 侧基础链路跑通。

请按“Agent SSE 流式改造方案”的第一批次执行。本轮只做 Java 后端基础能力，不接 Python streaming，不改 React UI，不删除旧接口。

## 本轮目标

完成：

1. 新增 agent_run_event 表和 Java 事件模型。
2. Agent run 创建接口改为异步：POST 创建 run 后立即返回 RUNNING + runCode。
3. 新增 Java -> React SSE 接口。
4. 后台暂时仍调用旧的 AgentRuntimeClient.run()。
5. 旧 JSON 执行完成后，通过 compatibility converter 把已落库的 run/step/action 转换成 agent_run_event，并通过 SSE 推给前端。

## 允许修改范围

允许修改：

* rag-backend 的 Agent run 相关代码
* Flyway migration
* AgentRunService / AgentController 相关类
* 新增 AgentRunExecutor
* 新增 AgentRunEventEntity / Mapper / Repository / Service / Response / Type
* 新增 AgentRunSseService
* 新增 AgentRunEventController 或在现有 Controller 增加 events 接口
* 相关配置类和单元测试

不允许修改：

* rag-ai-service Python 代码
* rag-frontend React 代码
* Python /v1/agent/runs 旧接口
* action confirm/reject 行为
* MCP 工具调用逻辑

## 关键要求

1. Flyway migration 版本号必须先扫描当前 db/migration 最新版本，再使用下一个版本号。
2. agent_run_event 按数据库 id 作为事件补发顺序，不依赖 Python createdAt。
3. 事件落库后必须通过 afterCommit 推送 SSE，不允许事务未提交就推送。
4. compatibility converter 只负责生成 agent_run_event 和 SSE 展示事件，不要重复创建 AgentStepEntity / AgentActionEntity。
5. compatibility terminal event 必须以 Java 最终 run.status 为准：

   * SUCCEEDED -> RUN_COMPLETED
   * FAILED -> RUN_FAILED
   * WAITING_CONFIRMATION -> RUN_WAITING_CONFIRMATION
6. /events 接口不使用 ApiResponse 包装，直接返回 SseEmitter。
7. /events 订阅需要支持 Last-Event-ID。
8. 订阅历史补发和 emitter 注册之间不能漏事件，请使用 runCode 级同步锁或等价机制。
9. React SSE 断开不影响后台任务。
10. 保持 Spring MVC，不引入 WebFlux。

## 验收标准

完成后请确保：

1. POST /api/knowledge-bases/{kbCode}/agent/runs 能快速返回 RUNNING，不等待 Python 完整执行。
2. 后台线程仍能调用旧 AgentRuntimeClient.run() 并完成原有 run/step/action 落库。
3. GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/events 可以 curl -N 看到 SSE。
4. agent_run_event 表有对应事件。
5. terminal 事件后 SSE 自动关闭。
6. mvn -q -pl rag-backend test 通过，或者说明不能通过的具体原因。

## 完成后请停止

本轮完成后只输出：

1. 改动摘要
2. 修改文件列表
3. 新增接口
4. 新增表结构
5. 本地验证命令
6. 当前未做的下一阶段事项

不要继续实现 Python streaming，不要改 React。

## 实施结果（2026-06-24）

本轮已完成：

1. 新增 `V19__create_agent_run_event_table.sql`：
   - 创建 `agent_run_event`
   - 给 `agent_step` 增加可空 `node_invocation_id`
   - 创建 `WHERE node_invocation_id IS NOT NULL` 的 partial unique index
2. Java 创建 run 已改为异步：
   - run 记录先在独立事务提交
   - 再提交 `rag-agent-*` 后台线程
   - HTTP `202` 立即返回 `RUNNING`、空 steps/actions
3. 后台线程继续调用旧 `/v1/agent/runs` JSON 接口。
4. 旧 Runtime 结果继续写入正式 `agent_step / agent_action / agent_run`。
5. compatibility converter 只读取正式表并创建前端事件，没有重复创建 step/action。
6. 新增 `/runs/{runCode}/events`，支持：
   - Spring MVC `SseEmitter`
   - 历史补发
   - `Last-Event-ID`
   - run 级同步锁
   - terminal 后关闭
7. 事件事务提交后通过 `@TransactionalEventListener(AFTER_COMMIT)` 推送。
8. Java runtime event 和 frontend event 已使用独立枚举隔离。

本轮验证：

1. `mvn -q -pl rag-backend -DskipTests compile` 通过。
2. `mvn -q -pl rag-backend test` 通过。
3. Flyway V19 已在本地 PostgreSQL 成功执行。
4. 实际创建 run 在约 `0.19s` 返回 `RUNNING`。
5. 后台旧 JSON Runtime 最终写入 9 条 step，run 状态为 `SUCCEEDED`。
6. Java SSE 补发 11 条事件且只有 1 条 terminal。
7. 带 `Last-Event-ID` 重连后只补发后续事件。

本轮未做：

1. Python `/v1/agent/runs/stream`
2. Java `AgentRuntimeStreamingClient`
3. streaming event applier
4. React EventSource Timeline
