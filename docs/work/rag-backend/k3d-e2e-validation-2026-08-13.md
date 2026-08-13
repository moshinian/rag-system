# Kubernetes 多实例 V1：K3d 端到端验证报告（2026-08-13）

## 1. 结论

在本机 K3d 双节点集群中，以同一后端镜像运行 `rag-backend x 4`，并联通 PostgreSQL、Redis、MinIO、`rag-ai-service` 和前端后，核心 V1 分布式链路通过验证：

- PostgreSQL `FOR UPDATE SKIP LOCKED` Claim 没有出现同一任务被两个实例持有。
- 上传 Pod 与执行 Pod 可以不同，执行实例能从 MinIO 读取原文件。
- Indexing 和 Agent 均能在 Owner Pod 消失后依赖 Lease/Heartbeat 被其他实例接管。
- 旧 Indexing Worker 恢复后被 Ownership Fence 拒绝，不能覆盖新 Owner 的结果。
- Agent 重试使用 `leaseVersion` 隔离 Runtime event/node 标识，不会被第一次尝试的幂等键误吞。
- Agent event 以 PostgreSQL 为历史权威、Redis Pub/Sub 做跨 Pod 实时通知，非执行 Pod 的 SSE 连接可收到事件；重连可从 PostgreSQL 补发。
- 四个后端实例持有四个不同 Snowflake Worker Lease；失去 Lease 的实例会退出 Ready，并由 liveness 触发重启。
- Rolling Update 中在途任务完成，未形成永久丢失；Pod 强制删除场景由 Lease Recovery 收敛。

测试集群在证据导出后删除，不保留测试 PVC 数据。

## 2. 验证环境

- K3d/K3s：1 server + 1 agent。
- Namespace：`rag-system`。
- 后端：4 replicas，`-Xms128m -Xmx384m`，request `100m/384Mi`，limit `1000m/640Mi`。
- 基础设施：PostgreSQL 16 + pgvector、Redis 7、MinIO，均为单实例学习配置。
- E2E 加速参数仅存在于 `k8s/overlays/e2e`：Heartbeat 5 秒、Lease 20 秒、Recovery 2 秒。基础清单继续使用更保守的默认值。
- Provider smoke：阿里云兼容 Embedding、DeepSeek Chat、Qwen Rerank；没有把真实凭证写入仓库或本报告。

## 3. 端到端业务证据

### 3.1 单副本基线

- 聚合健康接口返回 PostgreSQL、Redis、AI Gateway、Embedding、LLM 全部 `UP`；前端 Ingress 返回 HTTP 200。
- 知识库：`e2e-kb-20260813`。
- 文档：`DOC-346193640248442880`，MinIO 对象大小 622 bytes。
- Indexing：`346193986626650112`，`QUEUED -> RUNNING -> SUCCEEDED`，生成 2 chunks / 2 vectors。
- Hybrid Retrieval + Rerank：`rerankStatus=APPLIED`，模型 `qwen3-rerank`，返回 3 hits。
- RAG Ask：真实 Chat Provider 返回基于检索上下文的答案。
- Agent：`AR-346194606871937025` 成功，持久化 37 条事件并调用 Java MCP 工具。

### 3.2 四副本 Claim 与共享存储

批量创建四个任务：

- `346195727237652480`
- `346195727665483776`
- `346195727862607872`
- `346195728252678144`

四个任务全部成功，被三个不同 Worker Pod Claim。`E2E-CLAIM-1` 请求由一个 Pod 接收、由另一个 Pod 执行，证明 Task Creation 与 Execution 已解耦。四份上传文件均由 MinIO 提供，执行 Pod 不依赖上传 Pod 的本地磁盘。

本轮没有把“4 个任务恰好平均分到 4 个 Pod”作为正确性条件；`SKIP LOCKED` 保证互斥，不保证公平调度。

### 3.3 Indexing Crash、Recovery 与旧 Worker Fence

- 强删 Owner 后：源任务 `346197879951609856` 标记失败，仅创建一个 Recovery Child `346198050307465216`，由另一 Pod 成功完成；63 个 chunk index 全部唯一并完成 embedding。
- 精确模拟旧 Worker 暂停、新 Owner 接管、旧 Worker 恢复：源任务 `346201330181890048`、Recovery Child `346201415112351744`；最终文档 `DOC-346201266738839552` 为 `INDEXED`，210 chunks / 210 embeddings，无重复 chunk index。
- 旧 Worker 恢复后记录 `indexing.task.fenced`，数据库 Ownership 校验拒绝其继续提交；该 Pod 同时因 Snowflake Lease 丢失退出 Ready，随后被 liveness 重启。

验证中发现并修复：分布式 `DOCUMENT_INDEXING` 内部原先又创建无 Lease 的 `DOCUMENT_PROCESS` 子任务。Owner Crash 后该子任务会长期 `RUNNING`，并被活动任务唯一索引阻塞 Recovery Child。修复后分布式链路只使用父任务作为执行权威；手工同步处理仍保留独立审计任务。

### 3.4 Agent Crash 与执行尝试隔离

- Run：`AR-346205486808440833`。
- 第一次 Owner：`rag-backend-5f4c4f5796-r9ktt`，`leaseVersion=1`、`attemptCount=1`；运行中强制删除。
- 第二次 Owner：`rag-backend-5f4c4f5796-q952p`，`leaseVersion=2`、`attemptCount=2`。
- 最终状态：`WAITING_CONFIRMATION`，75 条持久事件，第二次尝试 41 条 `-A2` 事件，只有一个 terminal event，16 steps、1 action。

验证中发现并修复：Python 每次 Runtime 调用都从 `runCode-000001` 重新编号；若 eventCode 全局唯一，重试会被第一次尝试的幂等记录全部吞掉。Java 现在把 `leaseVersion` 纳入重试事件和 node invocation 标识：单次尝试内仍然幂等，不同合法执行尝试可以继续推进。

### 3.5 跨 Pod SSE 与历史补发

- `E2E-AGENT-CROSS` 请求进入 Pod A、Agent 由 Pod B 执行、SSE 明确连接到第三个 Pod；第三个 Pod 实时收到事件 38、39、40，证明 Redis Pub/Sub 跨实例通知有效。
- 浏览器语义的 `Last-Event-ID` 重连在另一个非 Owner Pod 上执行，补发 STEP_COMPLETED 与 RUN_COMPLETED，证明 PostgreSQL History Replay 不依赖 Redis 消息可靠性。
- 该跨 Pod 实时测试的 Agent 最终步骤遇到一次 Provider timeout，因此该 Run 业务终态为失败；独立的完整 Agent Run 已成功。SSE 数据面结果不依赖该 Provider 终态。

### 3.6 Snowflake 与 Rolling Update

- 四副本稳定时 Redis 仅存在四个 Worker Lease：worker 1、2、3、4，无重复槽位。
- 失去 Lease 的旧进程停止生成 ID、退出 Ready，并由 liveness 重启；没有采用“续租失败仍继续发号”的可用性冒险策略。
- Rolling Update 前任务 `346202998709555200` 处于 `RUNNING / DOCUMENT_PROCESSING`；滚动完成后任务由原 Owner 正常 `SUCCEEDED`，没有创建 Recovery Child，说明 SIGTERM 下 Executor Drain 有效。
- Snowflake allocator 使用 `SmartLifecycle` 在任务线程池停止后安全释放 token；滚动后 Redis 中只保留新 Pod 的四个 Lease。

## 4. 用户列出的 12 项验收结果

| Test | 结果 | 说明 |
| --- | --- | --- |
| 1. 四实例 Claim 一个任务 | PASS | DB 原子 Claim + 集成并发测试；单任务只有一个 Owner |
| 2. 四个 QUEUED Task | PASS | 全部完成、分布到三个 Pod；公平分布不属于互斥正确性保证 |
| 3. 请求 Pod A、执行 Pod D | PASS | Request ID 与 Worker Owner 日志确认不同 Pod |
| 4. 上传 Pod A、执行 Pod C | PASS | MinIO 对象可由任意 Worker 读取 |
| 5. 任务执行时删除 Pod | PASS | Lease 到期后唯一 Recovery Child 完成 |
| 6. 四 Scheduler 扫 stale task | PASS | 行锁 + 状态迁移只产生一次有效恢复 |
| 7. 旧 Worker 恢复 | PASS | Heartbeat/finish 受 Owner + leaseVersion Fence 约束 |
| 8. SSE Pod A、Agent Pod D | PASS | Redis Pub/Sub 实时跨 Pod 投递 |
| 9. Pub/Sub 丢失后重连 | PASS | Last-Event-ID 从 PostgreSQL 补发终态 |
| 10. 四 Pod Snowflake | PASS | 四个不同 Worker Lease |
| 11. Lease 过期且槽位重用 | PASS | 旧进程 Not Ready、停止发号并被重启 |
| 12. Rolling Update | PASS | 在途任务完成，Lease 无碰撞 |

额外验证：Agent Owner Pod 强删后由新 Owner 以新 leaseVersion 完成第二次尝试。

## 5. 回归与构建

- Java：`244` tests，`0` failures，`0` errors，`3` skipped。
- Python：`35 passed`。
- Frontend：TypeScript + Vite production build 成功，3184 modules。
- Kustomize：E2E overlay 成功渲染 290 行 YAML。
- Flyway：28 个 migration 全部校验通过；V28 移除 V27 与既有唯一索引重复创建的冗余索引。
- 三个生产 Dockerfile 均完成构建；K3d 最终运行镜像基于同一后端 JAR 和生产 JRE/non-root 运行层。
- `git diff --check` 通过。

## 6. 资源与连接预算快照

四个空闲/低负载 Backend Pod 的内存为 280、283、299、280 Mi，均低于 640 Mi limit，说明 `-Xmx384m` 对本次小型多实例实验可用，不需要上调到 1–2 GiB Heap。

- Backend CPU：约 8–10m/Pod（低负载快照）。
- AI Service：117 Mi。
- PostgreSQL：97 Mi；采样时 `rag_db` 12 个 session，`max_connections=100`。
- MinIO：241 Mi；Redis：4 Mi；Frontend：13 Mi。
- Hikari `maximumPoolSize=5/Pod`，四副本理论上限 20；当前实际数据库 session 明显低于 PostgreSQL 上限。
- Indexing 和 Agent concurrency 均为 1/Pod，因此各自集群并发上限为 4。

## 7. 已知边界与后续项

1. `EmbeddingRebuildService` 仍是本地异步管理任务，不具备与 Indexing/Agent 相同的 Claim/Lease/Recovery；V1 应视为运维低频入口，后续应迁移为数据库任务或明确单实例执行。
2. 当前业务写入前后都做 Ownership 检查，并以唯一索引保证重放幂等；若要进一步消除“检查后、写入前”的极窄 TOCTOU 窗口，可把 fencing token 下沉到具体业务 UPDATE/INSERT 条件中。
3. Redis Pub/Sub 只承诺实时提示，不承诺消息可靠；该限制由 PostgreSQL event history + Last-Event-ID 补发承担，属于设计选择。
4. 本地 PostgreSQL、Redis、MinIO 都是单实例学习环境，不代表基础设施高可用；生产环境应换成托管/高可用服务，但应用协议无需改变。
5. E2E 使用真实外部 Provider，供应商超时仍可能导致某次 Agent 失败；恢复与 SSE 验证应区分“分布式数据面正确性”和“模型供应商成功率”。

## 8. 复现入口

- 集群启动：`scripts/e2e/k3d-bootstrap.sh`
- 集群清理：`scripts/e2e/k3d-cleanup.sh`
- E2E 配置：`k8s/overlays/e2e`
- 测试步骤：`docs/work/rag-backend/kubernetes-distributed-test-plan.md`
- 架构与取舍：`docs/rfcs/RFC-0013-kubernetes-multi-instance-v1.md`
