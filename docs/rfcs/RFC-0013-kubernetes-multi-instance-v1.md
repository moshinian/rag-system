# RFC-0013: Kubernetes 同构多实例 V1

## 状态

Accepted，按阶段实施。V1 使用四个完全相同的 `rag-backend` Pod；不拆 API/Worker，不引入消息队列或 Leader Election。

## 当前单实例假设

- HTTP 请求创建 Indexing/Agent 记录后立即提交本 JVM Executor，任务归属于进程而不是集群。
- Indexing Recovery 先扫描再更新，缺少行锁、Owner、Lease 和 fencing，多个 Scheduler 会创建重复恢复任务。
- Agent 事件虽然持久化到 PostgreSQL，但 SSE 连接和 Spring Event 都在本 JVM。
- 上传文件保存为本机绝对路径，其他 Pod 无法读取。
- Snowflake 固定 `workerId=1`，多实例会破坏唯一性前提。
- Executor、Hikari 和 HTTP Client 配额都是单 JVM 口径，副本数会放大集群并发。

## V1 决策

1. PostgreSQL 同时承担任务状态与 V1 分布式队列；Claim 使用 `FOR UPDATE SKIP LOCKED`，保证状态变更和领取原子提交。
2. Indexing 与 Agent 分别维护可读的 Claim/Lease 逻辑，不抽象为万能任务框架。
3. 执行语义是 At-Least-Once；所有完成、心跳和阶段写入必须校验 `owner_instance_id + lease_version`。
4. 文件通过 `FileStorageService` 抽象；Kubernetes 使用 MinIO，本地仍可使用文件系统。
5. PostgreSQL 保存 Agent Event 历史，Redis Pub/Sub 只做实时跨实例通知，SSE 重连使用 `Last-Event-ID` 回放。
6. Snowflake 最终 ID 仍在 JVM 生成；Redis 只租赁 WorkerId。续租失败时停止发号并退出 Readiness。
7. 所有 Pod 都可运行 Poller 和 Recovery Scheduler；正确性来自数据库原子操作，而不是单例 Scheduler。
8. 优雅停机先停止 Claim，再等待在途任务；超时退出后由 Lease 过期与 Recovery 接管。

## 不变量

- 同一任务同一 `lease_version` 只有一个合法 Owner。
- 旧 Owner 不能续租、推进 stage 或写入终态。
- Recovery 对同一失效执行代只产生一个有效后继。
- Redis 丢消息不丢事件；本地磁盘丢失不丢业务文件。
- Redis WorkerId Lease 不健康时，实例不得继续生成 Snowflake ID。

## 分阶段交付

1. 单副本 Kubernetes、镜像、配置、Secret 和探针基线。
2. Local/MinIO 存储抽象与兼容迁移。
3. Instance ID、Snowflake WorkerId Lease、Readiness 和停机排水。
4. Indexing Claim/Lease/Heartbeat/Recovery/幂等性。
5. Agent Claim/Lease/Heartbeat/Recovery。
6. Redis Pub/Sub 跨实例 SSE 与数据库补偿回放。
7. 扩至四副本，治理线程池、数据库连接和外部 HTTP 并发。

## 明确不做

Kafka、RabbitMQ、Quartz Cluster、XXL-JOB、Leader Election、Service Mesh、Exactly-Once 声明，以及 V1 API/Worker Role Split。

## 当前实现边界与四副本放量门槛

本次已经实现 Indexing/Agent 的原子 Claim、Owner/Lease/Heartbeat/Fencing/Recovery，MinIO 存储、Redis WorkerId Lease、Redis Pub/Sub SSE 和 Kubernetes 基线。`k8s/base` 仍固定为单副本，四副本必须显式应用 `k8s/overlays/multi-instance`，避免尚未执行故障演练时误放量。

启用四副本前还必须完成以下门槛：

- 将 `EmbeddingRebuildService` 的本地提交与先查后改逻辑迁入同一种数据库 Claim/Lease 模型；它目前仍是遗留的单实例任务路径。
- 给 Chunk 重建、Embedding 写入和 Document 状态等业务副作用补齐幂等键或所有权栅栏；任务终态已经有 fencing，但 At-Least-Once 重放仍可能重复执行中间业务写入。
- 把历史 `storage_type=local` 文件复制进 MinIO 并更新 `object_key`。配置切换不会自动搬迁旧 Pod 文件。
- 在真实 Kubernetes 中执行本文测试计划的 Pod Kill、Lease 过期、旧 Worker 恢复、Redis 消息丢失和 Rolling Update 演练。

上述事项不影响单副本基线和本轮分布式控制面验证，但在完成前不应把四副本 Overlay 视为生产就绪。
