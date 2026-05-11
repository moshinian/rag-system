# RFC-0002 Readiness Gate

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录 `qa/readiness` 与真实检索入口统一收口为 readiness gate 的历史与现状。核心结论是：系统不再把“能发起查询”视为“可以安全回答”，而是将知识库状态、chunk 索引状态、embedding 状态和重建状态合并成一套可观察、可阻断、可缓存的检索就绪门禁。

## Context

项目在完成基础检索能力后，很快遇到两个典型问题：

1. 前端可能看到知识库“存在且可点”，但底层 chunk 尚未索引或向量未就绪。
2. 配置切换、重建进行中、维度不一致等异常下，如果仍然允许检索，请求会得到不可信结果。

早期接口已经提供 `GET /qa/readiness` 用于前端观察，但如果它只是“展示信息”而不参与真实流量控制，就会出现页面和实际行为不一致的问题。

## Decision

系统将 readiness 定义为真实门禁，而不是只读状态面板。

所有检索入口统一经过 `RetrievalReadinessService` 判断；`qa/readiness` 接口与真实检索共用同一份结论。只要 readiness 不满足，系统直接阻断检索和问答。

当前 gate 至少覆盖以下条件：

1. 知识库未激活。
2. rebuild 正在进行中。
3. rebuild 正在取消中。
4. rebuild 已取消但未恢复。
5. `reembedRequired=true`。
6. 没有 indexed chunks。
7. 没有 embedded chunks。
8. 当前活动向量维度与配置不一致。

## Historical Evolution

### Phase 1: readiness 作为前置观察接口出现

- 参考资料：`work day8`、`work day10`
- 特征：前端和联调开始需要一个明确接口，用于判断知识库是否具备问答前置条件。

### Phase 2: 检索链路扩大后，readiness 需要与真实行为一致

- 参考资料：`update/update1.md`
- 特征：随着 embedding profile、异步重建和取消重建等状态引入，readiness 不能只展示“看起来如何”，必须直接决定“能不能检索”。

### Phase 3: readiness 接入缓存与前端卡片

- 参考提交：`bab81c2` `Add Redis business caching and close week 3`
- 特征：`qa/readiness` 成为高频读路径的一部分，同时在前端卡片中承担运维可观察性。

## Implementation

核心实现位于：

1. [RetrievalReadinessService.java](../../rag-backend/src/main/java/com/example/rag/service/RetrievalReadinessService.java)
2. [QuestionAnsweringService.java](../../rag-backend/src/main/java/com/example/rag/service/QuestionAnsweringService.java)
3. [QaService.java](../../rag-backend/src/main/java/com/example/rag/service/QaService.java)

当前行为如下：

1. `QuestionAnsweringService.getReadiness()` 负责构造 readiness 响应，并使用 `CacheNames.QA_READINESS` 做缓存。
2. `QuestionAnsweringService.retrieve()` 在检索前调用 `retrievalReadinessService.assertRetrievalReady(kbCode)`。
3. `QaService.ask()` 走相同的 readiness gate，不绕过检索前置检查。

readiness 响应对象会显式返回：

1. `questionAnsweringReady`
2. `embeddingProvider`
3. `embeddingModel`
4. `activeEmbeddingModel`
5. `vectorDimensions`
6. `vectorStore`
7. `defaultTopK`
8. `indexedChunkCount`
9. `embeddedChunkCount`
10. `reembedRequired`
11. `reembedInProgress`
12. `currentRebuildRunId`
13. `nextStep`

前端展示位于 [readiness-card.tsx](../../rag-frontend/src/components/cards/readiness-card.tsx)。

## Cache And Rebuild Interaction

readiness gate 的一个关键实现点是，它不只是“判断”，还要与异步重建生命周期保持一致。

当前 [EmbeddingRebuildService.java](../../rag-backend/src/main/java/com/example/rag/service/EmbeddingRebuildService.java) 在异步重建完成或失败后，会主动清理 readiness、检索结果和相关文档 chunk 缓存，并清空 `currentRebuildRunId`。这样做是为了避免前端继续读到过期 readiness，或检索层继续命中过期缓存。

## User-Facing Contract

readiness gate 还有一个常被低估的价值：它已经成为前后端共享的操作提示协议。

从 [frontend plan.md](../../rag-frontend/work/frontend%20plan.md) 可以看出，前端工作台、知识库概览页、检索页和问答页都依赖 readiness 返回的 `questionAnsweringReady` 和 `nextStep` 决定 CTA 与页面引导。这意味着 readiness 不只是后端内部防线，还承担了：

1. 告诉用户“当前库能不能问答”。
2. 告诉用户“下一步该上传文档、补索引，还是等待重建完成”。
3. 保证界面状态与真实检索行为一致，而不是让用户点下去后才报错。

## Consequences

正面影响：

1. `qa/readiness` 与真实检索入口不会再各说各话。
2. embedding 迁移、重建取消、维度不一致等复杂场景都有统一阻断语义。
3. 前端卡片可以直接承担运维观察作用，而不是只展示表面状态。

代价与约束：

1. 检索前多了一层显式状态判断，系统逻辑比“直接查向量库”更复杂。
2. 一旦 gate 条件定义不准确，就可能误拦截真实可用流量。
3. readiness 缓存需要和重建、文档变更、索引变更保持严格一致，否则会出现短暂陈旧读。

## Non-Goals

本 RFC 不定义：

1. readiness 卡片的最终交互样式。
2. 运维后台的告警规则。
3. 更细粒度的 chunk 级局部就绪判断。

## Open Questions

1. `nextStep` 是否需要进一步结构化，而不是返回面向展示的文本。
2. readiness 是否要区分“可检索但不建议回答”和“完全阻断”两个级别。
3. 后续混合检索接入后，BM25、向量召回、重排序是否需要各自独立的 readiness 子状态。

## References

1. [update1.md](../../update/update1.md)
2. [README.md](../../README.md)
3. [week3.md](../../rag-backend/work/week3.md)
4. [current-status.md](../../rag-backend/work/current-status.md)
5. [frontend plan.md](../../rag-frontend/work/frontend%20plan.md)
6. Commit `bab81c2` `Add Redis business caching and close week 3`
