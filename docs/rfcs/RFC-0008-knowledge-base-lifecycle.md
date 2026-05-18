# RFC-0008 Knowledge Base Lifecycle

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录知识库 `ACTIVE / INACTIVE` 生命周期，以及文档 `DISABLED` 软下线语义的当前边界。核心结论是：知识库禁用是手工运维动作，不是切片或 embedding 失败后的自动惩罚；文档禁用也采用可逆的软下线模型，保留原文件、chunk 和向量，只把该文档排除出检索口径；恢复时，系统允许只恢复知识库/文档可用性，也允许顺手补偿最近一次可重试的失败索引任务，但这些动作必须显式区分。

## Context

随着项目进入多周迭代，知识库状态已经不再只是简单开关，而会影响：

1. 上传、索引、检索和问答是否允许继续。
2. readiness gate 是否直接阻断。
3. 前端工作台应该展示哪些 CTA。
4. 失败索引任务在恢复知识库后是否需要补偿。
5. 文档级禁用后，历史 chunk 和向量是否应该删除，还是只退出检索口径。

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

系统当前采用下面的文档级生命周期规则：

1. `DISABLED` 表示文档被手工软下线。
2. 文档禁用不会删除原文件、chunk 或向量。
3. 只要文档处于 `DISABLED`，它就不会参与 `qa/readiness` 统计、检索和问答。
4. 恢复文档时，优先回到禁用前状态；历史老数据如果没有记录禁用前状态，则按“有 chunk 则恢复为 `INDEXED`，有错误则恢复为 `FAILED`，否则恢复为 `UPLOADED`”回退。

## Historical Evolution

### Phase 1: 只有知识库开关，没有明确运维语义

- 特征：系统早期已经存在 `ACTIVE / INACTIVE`
- 局限：
  - 字段存在，但“什么情况下切换、谁来切换、是否自动切换”没有正式文档
  - 文档失败和知识库禁用容易被误解成同一层问题

### Phase 2: readiness gate 把知识库状态升级为真实阻断条件

- 特征：`RFC-0002` 收口后，知识库状态不再只是展示字段，而会真实影响检索和问答入口
- 结果：`INACTIVE` 开始具备明确的用户可见后果，但恢复和补偿语义还不完整

### Phase 3: 补齐恢复语义和文档软禁用

- 特征：
  - 知识库支持“恢复使用”与“恢复并重试失败任务”
  - 文档支持 `DISABLED` 软下线与恢复
  - 首页 readiness 统计和检索口径开始排除已禁用文档
- 结果：生命周期正式从“布尔开关”升级成前后端共享运维契约

## Implementation

核心实现位于：

1. [KnowledgeBaseService.java](../../rag-backend/src/main/java/com/example/rag/service/KnowledgeBaseService.java)
2. [KnowledgeBaseController.java](../../rag-backend/src/main/java/com/example/rag/controller/KnowledgeBaseController.java)
3. [DocumentIndexingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentIndexingService.java)
4. [DocumentService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentService.java)

当前行为如下：

1. `POST /api/knowledge-bases/{kbCode}/disable`
   把知识库切为 `INACTIVE`，并失效相关缓存。
2. `POST /api/knowledge-bases/{kbCode}/enable`
   把知识库切为 `ACTIVE`。
3. `POST /api/knowledge-bases/{kbCode}/enable?retryFailedIndexingTasks=true`
   在恢复知识库后，额外触发知识库级失败索引补偿。
4. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/disable`
   把文档切成 `DISABLED`，并失效 document/detail/chunks/readiness/retrieval 相关缓存。
5. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/enable`
   把文档恢复成可用状态；新数据优先恢复到禁用前状态，历史数据使用回退规则。

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
4. 文档列表页和文档详情页可直接执行“禁用文档 / 恢复文档”。
5. 知识库概览页里的“可检索已切块 / 可检索已向量化”明确只统计当前可参与检索的文档，不再把总文档数和可检索 chunk 口径混在一起。

这意味着知识库状态不再只是数据库字段，而是前后端共享的运维契约。

## Relationship To Other Decisions

1. `RFC-0002` 的 readiness gate 会在知识库 `INACTIVE` 时直接阻断检索。
2. `RFC-0004` 的索引任务恢复只解决任务层补偿，不负责切换知识库状态。
3. `RFC-0006` 的缓存策略要求禁用/恢复时同步失效相关 readiness 与 retrieval 缓存。
4. 文档 `DISABLED` 通过查询条件退出 readiness 和 retrieval 口径，不需要物理删除 chunk 或向量。

## Validation

当前已有直接验证材料：

1. [KnowledgeBaseServiceTest.java](../../rag-backend/src/test/java/com/example/rag/service/KnowledgeBaseServiceTest.java) 已覆盖知识库禁用、恢复和失败任务补偿分支。
2. [DocumentServiceTest.java](../../rag-backend/src/test/java/com/example/rag/service/DocumentServiceTest.java) 已覆盖文档禁用、恢复和历史状态回退规则。
3. README 已把知识库恢复、恢复并重试失败任务、文档软禁用/恢复和 readiness 口径变化写入当前完成情况。
4. 前端工作台当前已落地对应入口，说明这套生命周期语义已经进入实际用户路径，而不只是后端内部约定。

## Consequences

正面影响：

1. 知识库运维动作和失败任务补偿被显式分层。
2. 用户可以更安全地手工恢复知识库，而不是依赖隐式系统行为。
3. 前端能把“恢复服务能力”和“补偿历史失败任务”拆成不同按钮，减少误解。
4. 文档可以临时下线再恢复，避免为了短期排障或内容治理而删掉已有物料。

代价与约束：

1. 恢复时的批量失败任务补偿仍是“最近一次失败任务”粒度，不是完整批处理编排。
2. 当前不会自动识别“失败太多应否禁用知识库”，仍需要人工判断。
3. 如果知识库文档规模很大，恢复并补偿失败任务的操作反馈仍然只是一轮任务提交，不是完整任务看板。
4. 历史老文档如果是在没有 `disabled_from_status` 字段时被禁用，恢复时仍然只能使用回退规则，不能 100% 还原所有中间态。

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
2. [plan.md](../work/rag-frontend/plan.md)
3. [RFC-0002-readiness-gate.md](./RFC-0002-readiness-gate.md)
4. [RFC-0004-async-indexing-and-recovery.md](./RFC-0004-async-indexing-and-recovery.md)
5. [RFC-0006-retrieval-cache-strategy.md](./RFC-0006-retrieval-cache-strategy.md)
