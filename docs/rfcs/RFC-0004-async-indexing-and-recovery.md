# RFC-0004 Async Indexing And Recovery

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录文档索引链路为什么从同步 `process + embed` 演进为异步 `index` 任务，以及当前最小恢复模型的边界。核心结论是：索引被视为长链路后台任务，系统通过 `indexing_task` 承载状态、阶段、重试来源和心跳信息；在不引入外部调度系统的前提下，先实现“可提交、可观察、可手动重试、可自动恢复”的单服务版本。

## Context

Week 2 结束后，系统已经能同步完成文档处理、向量化、检索和问答，但同步链路很快暴露出几个问题：

1. 文档处理和 embedding 都是长链路，直接阻塞请求线程。
2. 调用方需要自己串联 `/process` 和 `/embed`，操作路径不稳定。
3. 缺少统一任务视角，无法观察排队、处理中、向量化中、完成、失败。
4. 服务异常退出后，任务可能停留在半途中，没有恢复入口。

因此，项目在 Week 3 先补异步索引，而不是继续扩问答功能。

## Decision

系统当前把“索引一篇文档”定义为后台任务，而不是前台同步动作。

核心决策如下：

1. 以 `POST /documents/{documentCode}/index` 作为主入口。
2. 后台任务统一串起 `process -> embed`。
3. 用 `indexing_task` 保存任务状态、任务阶段、重试次数、心跳和恢复信息。
4. 同一文档存在活跃索引任务时，拒绝重复提交。
5. `FAILED` 任务允许手动 retry。
6. 长时间无心跳的 `QUEUED / RUNNING` 任务允许自动 recovery。
7. 重试和恢复都生成新子任务，而不是覆盖原任务记录。

## Historical Evolution

### Phase 1: 文档处理有记录，但没有真正的后台编排

- 相关提交：`9703790` `Add PDF parsing and indexing task tracking`
- 特征：系统已经开始记录处理任务，但主链路仍以同步接口为主。

### Phase 2: 异步索引入口与最小任务编排落地

- 相关提交：`8edcf7d` `add async indexing flow for week3 day15`
- 特征：引入 `DocumentIndexingService`、`/index` 入口、`QUEUED` 状态和 `taskStage`。

### Phase 3: 失败重试与卡住任务恢复落地

- 相关提交：`b31dc93` `add indexing retry and recovery flow for day16`
- 特征：引入 `retry` 入口、`triggerSource`、`retryCount`、`lastHeartbeatAt`、`recoveredAt` 和最大重试边界。

### Phase 4: 状态一致性和缓存回归继续修正

- 相关提交：`764df97` `Fix indexing state consistency and cache regressions`
- 特征：索引不再只是“能跑”，还要保证状态语义与读路径一致。

## Implementation

核心实现位于 [DocumentIndexingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentIndexingService.java)。

当前任务模型包含两层语义：

1. 任务状态：`QUEUED / RUNNING / SUCCEEDED / FAILED`
2. 任务阶段：`QUEUED / DOCUMENT_PROCESSING / DOCUMENT_EMBEDDING / COMPLETED`

任务实体位于 [IndexingTaskEntity.java](../../rag-backend/src/main/java/com/example/rag/persistence/entity/IndexingTaskEntity.java)，当前关键字段包括：

1. `status`
2. `taskStage`
3. `triggerSource`
4. `parentTaskId`
5. `retryCount`
6. `maxRetryCount`
7. `lastHeartbeatAt`
8. `recoveredAt`
9. `chunkCount`
10. `embeddedChunkCount`

恢复来源目前分为：

1. `SUBMIT`
2. `MANUAL_RETRY`
3. `RECOVERY`

相关状态查询和并发保护主要通过 [IndexingTaskRepository.java](../../rag-backend/src/main/java/com/example/rag/persistence/IndexingTaskRepository.java) 实现。

文档处理与向量化本身仍复用现有服务：

1. [DocumentProcessingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentProcessingService.java)
2. [DocumentEmbeddingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentEmbeddingService.java)

## Recovery Model

当前恢复模型刻意保持简单，不引入 MQ、Quartz 或分布式调度。

系统只解决最小恢复问题：

1. 手动 retry 仅允许针对 `FAILED` 任务。
2. 自动 recovery 仅针对长时间无心跳的 `QUEUED / RUNNING` 任务。
3. 每次 retry/recovery 都生成新子任务，保留原任务用于审计和排障。
4. 通过 `maxRetryCount` 限制无限恢复。

这意味着当前版本的目标不是“全局调度最优”，而是“单服务崩掉后不要让任务永久悬空”。

## User-Facing Contract

这个 RFC 不只是后端内部实现，因为前端主操作流已经围绕异步索引设计。

从 [frontend plan.md](../../rag-frontend/work/frontend%20plan.md) 可以看到，用户主路径是：

1. 上传文档。
2. 触发 `/index`。
3. 轮询 `document detail + indexing-tasks`。
4. 在详情页查看阶段时间线、错误信息和 retry 按钮。

因此，`indexing_task` 的状态语义已经成为前后端共享契约，而不是单纯的数据库内部字段。

## Validation

这个主题已经有直接的测试和历史材料支撑：

1. [DocumentIndexingServiceTest.java](../../rag-backend/src/test/java/com/example/rag/service/DocumentIndexingServiceTest.java) 覆盖提交、重试、并发保护和恢复场景。
2. [work day15.md](../../rag-backend/work/work%20day15.md) 记录了异步索引起步背景。
3. [work day16.md](../../rag-backend/work/work%20day16.md) 记录了 retry 与 recovery 的目标和边界。

## Consequences

正面影响：

1. 长链路不再占用请求线程。
2. 调用方从多个同步动作收敛到一个主入口。
3. 失败和恢复有了可观察、可追踪的历史记录。
4. 前端可以围绕任务阶段构建真正的工作台，而不是靠用户猜当前状态。

代价与约束：

1. 状态机、心跳和恢复逻辑让系统复杂度明显上升。
2. 当前模型仍然是单服务内调度，不适合直接外推到多实例抢占场景。
3. 自动恢复只能解决“最小可恢复”，不能替代真正的分布式任务系统。

## Non-Goals

本 RFC 不定义：

1. 多实例任务协调。
2. 任务取消。
3. 任务中心级分页检索和全局看板。
4. MQ/作业系统级别的编排框架。

## Open Questions

1. 后续是否要把索引任务升级为知识库级批处理，而不只是单文档任务。
2. 多实例部署时，恢复扫描如何避免重复抢占。
3. 是否需要把 `taskStage` 再细分到 chunk 级进度，而不只是处理/向量化两阶段。

## References

1. [README.md](../../README.md)
2. [week3.md](../../rag-backend/work/week3.md)
3. [current-status.md](../../rag-backend/work/current-status.md)
4. [work day15.md](../../rag-backend/work/work%20day15.md)
5. [work day16.md](../../rag-backend/work/work%20day16.md)
6. [frontend plan.md](../../rag-frontend/work/frontend%20plan.md)
7. Commit `9703790` `Add PDF parsing and indexing task tracking`
8. Commit `8edcf7d` `add async indexing flow for week3 day15`
9. Commit `b31dc93` `add indexing retry and recovery flow for day16`
10. Commit `764df97` `Fix indexing state consistency and cache regressions`
