# 第六轮：稳定性收口：202 兼容与 RUNNING 恢复兜底

## 本轮目标

本轮只做发布前稳定性收口：

1. 确认前端兼容 `POST /agent/runs` 返回 `202 Accepted`。
2. 新增持久化 `runtime_heartbeat_at`，记录 Java 最近一次确认 Python Runtime stream 活跃的时间。
3. 新增 `AgentRunRecoveryScheduler`，避免 Agent run 长期停留在 `RUNNING`。

不引入 WebFlux、Redis Pub/Sub、MQ 或自动重试；不改变 confirm/reject 语义；不让 React 直连 Python；不做 LLM token streaming。

## 实施结果（2026-06-24）

### 前端 202 兼容

已检查 `rag-frontend/src/api/client.ts`：

- 前端使用 `response.ok` 判断 HTTP 成功。
- `response.ok` 接受所有 2xx 状态，包括 `202 Accepted`。
- 创建 run 成功后仍从响应体读取 `runCode`。
- 拿到 `runCode` 后仍通过 EventSource 连接 Java `/events` 接口。

因此本轮无需修改前端创建 run 逻辑。

### 数据库迁移

新增：

```text
rag-backend/src/main/resources/db/migration/V20__add_agent_run_runtime_heartbeat.sql
```

内容：

- 给 `agent_run` 增加 `runtime_heartbeat_at TIMESTAMPTZ`。
- 对迁移时已有 `status = 'RUNNING' AND runtime_heartbeat_at IS NULL` 的 run 回填 `now()`。
- 已终态 run 保持 `runtime_heartbeat_at = NULL`。
- 增加 `agent_run(status, created_at, runtime_heartbeat_at)` 辅助索引。

### Runtime heartbeat

新增：

- `AgentRunHeartbeatService`
- `AgentRunMapper.updateRuntimeHeartbeatToNow(runCode)`
- `AgentRunRepository.updateRuntimeHeartbeatToNow(runCode)`

规则：

- 创建 RUNNING run 时初始化 `runtime_heartbeat_at`。
- `SseEventParser` 读取到 `: heartbeat` 时立即回调，不等待空行提交 event。
- heartbeat 不写入 `agent_run_event`。
- heartbeat 不推送 React。
- heartbeat 持久化使用数据库 `now()`，避免 Java 应用时间与 DB 时间不一致。
- Java 侧默认至少间隔 30 秒才更新一次 heartbeat，避免频繁写库。
- 普通 Python runtime event 成功消费时也会 touch heartbeat。
- 更新 heartbeat 失败时只记录结构化日志，不中断 stream。
- run terminal 或后台任务结束后清理内存节流记录。

### Recovery Scheduler

新增：

- `AgentRunRecoveryScheduler`
- `AgentRunRecoveryService`
- `RagAgentProperties.recovery`

已确认 `RagApplication` 已启用 `@EnableScheduling`，无需额外添加调度开关注解。

默认配置：

```yaml
rag:
  agent:
    recovery:
      enabled: true
      scan-interval-seconds: 60
      running-timeout-minutes: 10
      idle-timeout-minutes: 3
      heartbeat-update-interval-seconds: 30
```

开发调试时如果需要长时间挂起 Agent run，可以临时设置：

```yaml
rag:
  agent:
    recovery:
      enabled: false
```

或调大 `running-timeout-minutes / idle-timeout-minutes`。

Recovery 扫描条件：

- `run.status = RUNNING`
- `run.created_at < runningCutoff`
- 没有 terminal `agent_run_event`
- `runtime_heartbeat_at IS NULL OR runtime_heartbeat_at < idleCutoff`
- `idleCutoff` 之后没有新的 `agent_run_event`

Recovery 动作：

- 同一事务内条件更新 run 为 `FAILED` 并写入 Java 合成 `RUN_FAILED` terminal event。
- 如果 event persist 失败，事务回滚 run 状态更新。
- 只有事务提交成功后，afterCommit listener 才推送 SSE。
- 不自动重试整个 Agent run。

Recovery event payload：

```json
{
  "source": "JAVA_RECOVERY",
  "reason": "RUNNING_TIMEOUT",
  "message": "Agent run recovery timeout: no runtime heartbeat or business event received for configured idle timeout."
}
```

### 并发保护

1. Recovery 条件更新会再次检查 run 仍为 `RUNNING`、heartbeat stale、没有 recent business event、且没有 terminal event。
2. 如果 scan 后刚收到 heartbeat，条件更新返回 0，不写 recovery event。
3. 如果 run 已进入 `SUCCEEDED / FAILED / WAITING_CONFIRMATION`，迟到 Python runtime event 会在落库前被忽略。
4. 忽略迟到 event 时输出结构化日志，包含：
   - `runCode`
   - `eventCode`
   - `runtimeEventType`
   - `currentRunStatus`

## 验收命令

后端测试：

```bash
mvn -q -pl rag-backend test
```

前端构建：

```bash
cd rag-frontend
npm run build
```

## 实际验证结果

已执行并通过：

```bash
mvn -q -pl rag-backend test
# passed

cd rag-frontend
npm run build
# passed
```

本轮未修改 Python Runtime 代码，因此未重复执行 Python 测试。

## 当前边界

1. 不自动重试 Agent run。
2. SSE emitter registry 仍是单 Java 实例内存态。
3. heartbeat 不作为业务 event，不进入 `agent_run_event`。
4. cancellation 仍是协作式，不强行中断正在阻塞的 LLM/tool 调用。
5. 多实例部署后，afterCommit event 需要 Redis Pub/Sub 或 MQ 广播。
