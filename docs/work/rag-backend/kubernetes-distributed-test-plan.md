# Kubernetes 分布式验收

## 前置观测

```sql
SELECT id, status, task_stage, owner_instance_id, lease_version, lease_until
FROM indexing_task ORDER BY created_at DESC LIMIT 20;

SELECT run_code, status, owner_instance_id, lease_version, lease_until, attempt_count
FROM agent_run ORDER BY created_at DESC LIMIT 20;
```

## 必测场景

1. 四个 Pod 并发 Poll 一个 `QUEUED` Indexing Task，断言只有一行进入 `RUNNING`，且只有一个 Owner。
2. 连续创建四个任务，观察不同 Pod Claim；请求 Pod 与执行 Pod 不应有绑定关系。
3. 上传进入 Pod A、任务由 Pod C Claim，确认 C 从 MinIO 下载同一 `object_key`。
4. 执行期间删除 Owner Pod；等待 120 秒 Lease 过期和 Recovery，断言只有一个恢复子任务。
5. 新 Owner 接管后恢复旧 Pod，旧 `lease_version` 的 Heartbeat、Stage 和终态 UPDATE 必须影响 0 行。
6. SSE 连接位于 Pod A、Agent 位于 Pod D，确认 Redis 通知后实时收到事件；阻断一次 Pub/Sub 后以 `Last-Event-ID` 重连并从 PostgreSQL 回放。
7. 四 Pod 获得不同 Snowflake WorkerId；停止 Redis 超过 TTL 后，实例 Readiness 失败且新建业务拒绝发号。
8. 执行 Rolling Update，确认 Poller 在 SIGTERM 后停止 Claim，在途任务完成或由 Lease Recovery 接管。

系统语义为 At-Least-Once。验收不能以“事件看起来只出现一次”代替幂等性、Owner fencing 和唯一约束检查。
