# RFC-0007 QA Contract: Answer, Sources And History

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录当前问答接口的结果契约，包括 `answer`、`retrievalResults`、`sources` 和 `/qa/history` 的关系。核心结论是：系统把“检索证据”和“回答展示”明确分层；`retrievalResults` 保留完整召回上下文，`sources` 提供更适合前端展示和追溯的精简来源结构，历史记录则按单问单答持久化这两层结果，以便回放和解释。

## Context

Week 2 的目标不是只返回一段模型文本，而是形成一个最小可用、可解释、可回看的 RAG 闭环。

如果问答接口只返回 `answer`，会立刻出现几个问题：

1. 前端无法展示来源证据。
2. 用户无法判断答案来自哪些文档和 chunk。
3. 排障时无法区分“检索错了”还是“模型组织错了”。
4. 历史记录无法回放当时的证据上下文。

因此，项目在 Day 12 和 Day 13 明确把“回答结果”和“检索证据”一并纳入接口与持久化契约。

## Decision

系统当前将问答结果分成三层：

1. `answer`
   面向最终阅读的回答文本。
2. `retrievalResults`
   面向调试、回放和内部排障的完整检索结果。
3. `sources`
   面向前端展示和证据追溯的精简来源列表。

同时，`/qa/history` 会持久化并返回：

1. 问题与答案。
2. 模型名、`topK`、延迟、`promptTemplate`。
3. 当时的 `retrievalResults`。
4. 当时的 `sources`。

## Historical Evolution

### Phase 1: 先形成最小问答闭环

- 相关提交：`29507a6` `implement day10 retrieval and prepare day11`
- 特征：系统已经具备 query embedding、TopK 检索和回答生成。

### Phase 2: 补充 `sources` 契约

- 相关提交：`fb4e146` `add qa sources response for day12`
- 特征：`/qa/ask` 开始返回结构化来源，前端不再需要从完整检索对象里自己猜字段。

### Phase 3: 补充历史记录契约

- 相关提交：`1750636` `persist qa history for day13 and prepare day14`
- 特征：`chat_session / chat_message` 和 `/qa/history` 落地，问答结果开始可回放。

## Implementation

当前问答主编排位于 [QaService.java](../../rag-backend/src/main/java/com/example/rag/service/QaService.java)。

当前实现逻辑是：

1. 先通过 `QuestionAnsweringService.retrieve()` 获得检索结果。
2. 用检索结果构造 prompt。
3. 调用 chat model 得到 `answer`。
4. 从 `retrievalResults` 映射出更轻量的 `sources`。
5. 把完整结果交给 `QaRecordService` 做持久化。

当前对外返回对象位于 [QaAnswerResponse.java](../../rag-backend/src/main/java/com/example/rag/model/response/QaAnswerResponse.java)，字段为：

1. `question`
2. `answer`
3. `topK`
4. `chatModel`
5. `retrievalResults`
6. `sources`

其中 `sources` 的字段位于 [QaSourceResponse.java](../../rag-backend/src/main/java/com/example/rag/model/response/QaSourceResponse.java)，当前包含：

1. `documentCode`
2. `documentName`
3. `chunkId`
4. `chunkIndex`
5. `content`
6. `score`
7. `startOffset`
8. `endOffset`

## Why Keep Both RetrievalResults And Sources

系统没有把 `sources` 直接等同于 `retrievalResults`，这是刻意设计。

原因是：

1. `retrievalResults` 更像内部检索调试对象。
2. `sources` 更像稳定的前端展示契约。
3. 前端展示和历史回放只需要一部分证据字段，不需要直接依赖完整检索对象的内部演化。

[QaService.java](../../rag-backend/src/main/java/com/example/rag/service/QaService.java) 里已经明确写了这一点：`sources` 只保留回答展示和追溯所需字段，避免直接暴露完整检索对象。

## History Contract

问答历史由 [QaRecordService.java](../../rag-backend/src/main/java/com/example/rag/service/QaRecordService.java) 持久化和查询。

当前持久化模型有一个重要边界：

1. 每次问答都会创建独立 `chat_session`。
2. 当前并不复用 session。
3. 历史页应被理解为“单问单答记录列表”，而不是连续多轮对话线程。

当前历史返回对象位于 [QaHistoryRecordResponse.java](../../rag-backend/src/main/java/com/example/rag/model/response/QaHistoryRecordResponse.java)，包含：

1. `sessionCode`
2. `sessionName`
3. `messageCode`
4. `question`
5. `answer`
6. `chatModel`
7. `topK`
8. `latencyMs`
9. `promptTemplate`
10. `retrievalResults`
11. `sources`
12. `createdAt`

这意味着历史接口不是简单“查文本”，而是查回一次问答的完整证据快照。

## Frontend Contract

这个主题已经直接影响前端结构。

从 [frontend plan.md](../../rag-frontend/work/frontend%20plan.md) 可以看出：

1. 问答页会同时展示答案、检索结果和来源。
2. 来源抽屉会基于 `sources` 做证据回看。
3. 历史页会展示 `sessionCode / sessionName / question / answer / sources`。
4. 当前前端被明确要求不要先按“连续聊天线程”设计，因为后端还没有 session 复用。

因此，`sources` 和 `/qa/history` 已经是明确的前后端共享契约，而不是后端内部附加字段。

## Relationship To Evaluation

这个 RFC 也直接支撑评测工作。

Day 20 评测里出现的 `retrievalHit`、`answerAcceptable`、`sourceStable` 三类判断，本质上都依赖当前问答契约：

1. `retrievalHit` 依赖检索结果是否命中正确文档。
2. `answerAcceptable` 依赖最终 `answer`。
3. `sourceStable` 依赖 `sources` 是否与回答一致。

所以这个契约不仅服务产品展示，也服务效果验证。

## Validation

当前已有直接材料支撑：

1. [QaServiceTest.java](../../rag-backend/src/test/java/com/example/rag/service/QaServiceTest.java) 验证 `sources` 映射结果。
2. [QaRecordServiceTest.java](../../rag-backend/src/test/java/com/example/rag/service/QaRecordServiceTest.java) 验证问答记录持久化、配置和 sessionName 处理。
3. [week2.md](../../rag-backend/work/week2.md)、[work day12.md](../../rag-backend/work/work%20day12.md)、[work day13.md](../../rag-backend/work/work%20day13.md) 记录了契约落地过程。

## Consequences

正面影响：

1. 回答结果具备可解释性，不再只有裸答案。
2. 前端可以稳定展示来源，而不是直接耦合完整检索对象。
3. 历史记录可以回放一次问答当时的证据上下文。

代价与约束：

1. 持久化体积会比只存答案更大。
2. `retrievalResults` 和 `sources` 两层结构需要保持一致语义。
3. 当前单问单 session 模型还不能满足真正的多轮对话产品形态。

## Non-Goals

本 RFC 不定义：

1. session 复用。
2. 多轮对话记忆。
3. 来源高亮在原文中的完整 UI 交互。
4. 更复杂的 citation ranking 或来源去重策略。

## Open Questions

1. 后续多轮对话落地时，历史模型是复用现有 `chat_session`，还是重新定义对话层。
2. `sources` 是否需要进一步收缩为更稳定的公共契约，减少与内部检索对象的隐式耦合。
3. 是否需要为历史记录增加“最终是否命中预期来源”的评测标记字段。

## References

1. [README.md](../../README.md)
2. [week2.md](../../rag-backend/work/week2.md)
3. [current-status.md](../../rag-backend/work/current-status.md)
4. [work day12.md](../../rag-backend/work/work%20day12.md)
5. [work day13.md](../../rag-backend/work/work%20day13.md)
6. [work day14.md](../../rag-backend/work/work%20day14.md)
7. [work day20.md](../../rag-backend/work/work%20day20.md)
8. [frontend plan.md](../../rag-frontend/work/frontend%20plan.md)
9. Commit `fb4e146` `add qa sources response for day12`
10. Commit `1750636` `persist qa history for day13 and prepare day14`
