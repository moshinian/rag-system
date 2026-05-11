# RFC-0008 Knowledge Base Lifecycle

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录知识库 `ACTIVE / INACTIVE` 生命周期的当前语义，以及“恢复使用”“恢复并重试失败索引任务”的边界。核心结论是：知识库禁用是手工运维动作，不是切片或 embedding 失败后的自动惩罚；恢复知识库时，系统允许只恢复检索/问答可用性，也允许顺手补偿最近一次可重试的失败索引任务，但这两件事必须显式区分。

## Context

随着项目进入多周迭代，知识库状态已经不再只是简单开关，而会影响：

1. 上传、索引、检索和问答是否允许继续。
2. readiness gate 是否直接阻断。
3. 前端工作台应该展示哪些 CTA。
4. 失败索引任务在恢复知识库后是否需要补偿。

实际代码里早已有 `ACTIVE / INACTIVE`，但过去存在一个容易混淆的点：

1. 文档处理或 embedding 失败会留下失败任务和失败文档。
2. 这些失败不会自动把知识库切成 `INACTIVE`。
3. 知识库禁用目前是运维人员手工操作，而不是系统自动熔断。

这次新增的恢复语义需要把这层边界正式说清楚。

## Decision

系统当前采用下面的知识库生命周期规则：

1. `ACTIVE`
   允许继续上传、索引、检索和问答。
2. `INACTIVE`
   表示知识库被手工禁用；检索和问答会被 readiness gate 阻断。

在当前实现里：

1. 禁用知识库不会删除任何文档、chunk、索引任务或问答历史。
2. 失败索引任务不会自动把知识库状态切换为 `INACTIVE`。
3. 恢复知识库时，支持两种显式动作：
   - 只恢复知识库使用状态。
   - 恢复知识库并重试每篇文档最近一次可重试的失败索引任务。

## Implementation

核心实现位于：

1. [KnowledgeBaseService.java](../../rag-backend/src/main/java/com/example/rag/service/KnowledgeBaseService.java)
2. [KnowledgeBaseController.java](../../rag-backend/src/main/java/com/example/rag/controller/KnowledgeBaseController.java)
3. [DocumentIndexingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentIndexingService.java)

当前行为如下：

1. `POST /api/knowledge-bases/{kbCode}/disable`
   把知识库切为 `INACTIVE`，并失效相关缓存。
2. `POST /api/knowledge-bases/{kbCode}/enable`
   把知识库切为 `ACTIVE`。
3. `POST /api/knowledge-bases/{kbCode}/enable?retryFailedIndexingTasks=true`
   在恢复知识库后，额外触发知识库级失败索引补偿。

补偿逻辑当前只会重试：

1. 同文档最近一次任务状态为 `FAILED` 的索引任务。
2. 且该任务尚未超过 `maxRetryCount`。
3. 且该文档不是 `DISABLED`。
4. 且该文档当前不存在活跃索引任务。

## User-Facing Contract

这个生命周期规则已经直接进入前端工作台：

1. 知识库列表页可直接执行禁用、恢复使用、恢复并重试失败任务。
2. 知识库概览页集中展示手工禁用状态、失败文档数、重嵌入入口和恢复动作。
3. 页面文案明确说明：当前知识库不会因为切片或 embedding 失败自动被禁用。

这意味着知识库状态不再只是数据库字段，而是前后端共享的运维契约。

## Relationship To Other Decisions

1. `RFC-0002` 的 readiness gate 会在知识库 `INACTIVE` 时直接阻断检索。
2. `RFC-0004` 的索引任务恢复只解决任务层补偿，不负责切换知识库状态。
3. `RFC-0006` 的缓存策略要求禁用/恢复时同步失效相关 readiness 与 retrieval 缓存。

## Consequences

正面影响：

1. 知识库运维动作和失败任务补偿被显式分层。
2. 用户可以更安全地手工恢复知识库，而不是依赖隐式系统行为。
3. 前端能把“恢复服务能力”和“补偿历史失败任务”拆成不同按钮，减少误解。

代价与约束：

1. 恢复时的批量失败任务补偿仍是“最近一次失败任务”粒度，不是完整批处理编排。
2. 当前不会自动识别“失败太多应否禁用知识库”，仍需要人工判断。
3. 如果知识库文档规模很大，恢复并补偿失败任务的操作反馈仍然只是一轮任务提交，不是完整任务看板。

## Non-Goals

本 RFC 不定义：

1. 基于失败阈值自动禁用知识库。
2. 文档级自动恢复策略替代知识库级手工操作。
3. 批量任务中心。
4. 多实例下的恢复编排。

## Open Questions

1. 后续是否需要引入“只恢复知识库，不允许上传/索引”的中间状态。
2. 是否需要为恢复操作返回更细的失败任务补偿明细，而不只是摘要计数。
3. 大规模知识库场景下，恢复并补偿失败任务是否要改成真正的异步批处理。

## References

1. [README.md](../../README.md)
2. [frontend plan.md](../../rag-frontend/work/frontend%20plan.md)
3. [RFC-0002-readiness-gate.md](./RFC-0002-readiness-gate.md)
4. [RFC-0004-async-indexing-and-recovery.md](./RFC-0004-async-indexing-and-recovery.md)
5. [RFC-0006-retrieval-cache-strategy.md](./RFC-0006-retrieval-cache-strategy.md)
