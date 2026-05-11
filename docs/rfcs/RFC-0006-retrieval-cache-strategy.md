# RFC-0006 Retrieval Cache Strategy

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录当前 Redis 业务缓存如何覆盖知识库、文档、chunk、`qa/readiness` 与检索结果，以及为什么系统优先选择“短 TTL + 写路径失效 + 必要时全量清理”的一致性优先策略。核心结论是：缓存当前只服务读路径加速，不承担复杂协调职责；一旦底层状态可能变化，系统宁可多清缓存，也不冒险返回过期检索结论。

## Context

Week 3 之前，Redis 在项目里更多承担连通性验证和健康探针作用。但随着系统进入真实问答链路，几个高频读路径开始重复出现：

1. 知识库详情与分页列表。
2. 文档详情、分页和 chunk 列表。
3. `qa/readiness`。
4. 相同问题下的检索结果。

如果这些请求全部直穿数据库和向量检索层，会带来两个问题：

1. 前端轮询和反复刷新会放大读压力。
2. readiness 与 retrieval 都可能成为高频重复调用路径。

但这个阶段系统更在意正确性而不是缓存命中率，因此缓存策略必须尽量简单、可解释、可失效。

## Decision

系统当前采用 Spring Cache + Redis 作为第一版业务缓存。

核心决策如下：

1. 只缓存读路径，不缓存写路径结果。
2. 按功能域拆分独立 cache name。
3. 每个 cache 使用独立 TTL。
4. 写路径发生状态变更时，主动执行 `@CacheEvict`。
5. 对难以细粒度定位影响范围的场景，允许直接 `allEntries=true`。
6. embedding rebuild 完成或失败后，允许对相关缓存执行全量清理。

## Historical Evolution

### Phase 1: Redis 只承担最小基础能力

- 相关背景：Week 1 / Day 3
- 特征：Redis 已接通，并提供健康探针，但还没有进入业务读路径。

### Phase 2: Redis 进入业务缓存

- 相关提交：`bab81c2` `Add Redis business caching and close week 3`
- 特征：知识库、文档、chunk、`qa/readiness` 和检索结果开始接入缓存。

### Phase 3: 缓存配置与状态一致性继续修正

- 相关提交：`3d9cf4d` `Close week 3 review and fix Redis cache config`
- 相关提交：`764df97` `Fix indexing state consistency and cache regressions`
- 特征：系统开始更明确地区分“什么该缓存”“什么时候必须失效”。

## Implementation

缓存名称集中定义在 [CacheNames.java](../../rag-backend/src/main/java/com/example/rag/config/CacheNames.java)：

1. `knowledgeBaseDetail`
2. `knowledgeBasePage`
3. `documentDetail`
4. `documentPage`
5. `documentChunks`
6. `qaReadiness`
7. `qaRetrieval`

缓存管理器配置位于 [RedisCacheConfig.java](../../rag-backend/src/main/java/com/example/rag/config/RedisCacheConfig.java)。

当前实现特征包括：

1. 使用 `rag:` 作为 key 前缀。
2. value 使用 JSON serializer。
3. 空值不缓存。
4. 每类缓存从 `rag.cache.*` 读取 TTL。
5. TTL 下限被限制为至少 `30s`。

## Coverage

当前缓存覆盖范围主要集中在这些服务：

1. [KnowledgeBaseService.java](../../rag-backend/src/main/java/com/example/rag/service/KnowledgeBaseService.java)
2. [DocumentService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentService.java)
3. [DocumentProcessingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentProcessingService.java)
4. [DocumentEmbeddingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentEmbeddingService.java)
5. [QuestionAnsweringService.java](../../rag-backend/src/main/java/com/example/rag/service/QuestionAnsweringService.java)
6. [EmbeddingRebuildService.java](../../rag-backend/src/main/java/com/example/rag/service/EmbeddingRebuildService.java)

其中最关键的两类缓存是：

1. `qaReadiness`
   用于前端概览页、检索页和问答页反复读取的就绪状态。
2. `qaRetrieval`
   用于相同问题和 `topK` 下的短 TTL 检索结果复用。

## Invalidation Strategy

当前系统优先保证一致性，因此失效策略相对保守。

典型规则包括：

1. 知识库启用、停用、删除后，失效相关 detail/page/readiness/retrieval 缓存。
2. 文档上传、禁用、删除后，失效相关 document/detail/chunks/readiness/retrieval 缓存。
3. 文档处理与向量化完成后，失效 chunk/readiness/retrieval 缓存。
4. embedding rebuild 提交、完成或失败后，清理 readiness、retrieval 与 chunk 缓存。

其中一个刻意的取舍是：`qaRetrieval` 经常使用 `allEntries=true`，而不是做知识库级精确键失效。原因是当前项目更在意“不要读到脏检索结果”，而不是“把缓存键设计到最细”。

## Why Short TTL And Broad Eviction

当前不追求复杂缓存编排，原因很现实：

1. 检索正确性比缓存命中率更重要。
2. embedding、chunk、文档状态和 readiness 会互相影响，很容易形成脏缓存链。
3. 当前系统仍在快速演进阶段，过早做复杂键设计会增加维护成本和回归风险。

所以当前策略是：

1. 能精确失效的地方就精确失效。
2. 不好精确界定影响面的地方就直接清全局相关 cache。
3. 用短 TTL 把陈旧窗口压小。

## Relationship To Other Decisions

这个主题与其他 RFC 有直接关联：

1. `RFC-0002` 的 readiness gate 依赖 `qaReadiness` 缓存，但其结论必须与真实阻断行为保持一致。
2. `RFC-0004` 的异步索引会触发文档、chunk、readiness 和 retrieval 的缓存失效。
3. `RFC-0001` 的 embedding rebuild 会触发最激进的一类缓存清理。

## Validation

当前已有直接材料支撑：

1. [RedisCacheConfigTest.java](../../rag-backend/src/test/java/com/example/rag/config/RedisCacheConfigTest.java) 验证 Redis serializer 与配置行为。
2. [README.md](../../README.md) 记录了真实 Redis 联调和缓存写入观察。
3. [week3.md](../../rag-backend/work/week3.md) 与 [current-status.md](../../rag-backend/work/current-status.md) 记录了缓存接入范围与工程取舍。

## Consequences

正面影响：

1. 高频读路径得到明显加速空间。
2. 前端轮询和反复刷新不会总是直打数据库与检索链路。
3. 缓存行为和失效规则仍然足够简单，便于排障。

代价与约束：

1. `allEntries=true` 会牺牲一部分命中率。
2. 当前还没有做到知识库级、问题级的最细粒度精确失效。
3. 一旦缓存失效点漏掉，就可能出现 readiness 与 retrieval 的陈旧读回归。

## Non-Goals

本 RFC 不定义：

1. 分布式锁或缓存级并发协调。
2. 多级缓存。
3. 基于消息总线的缓存广播失效。
4. retrieval 结果的长期离线缓存。

## Open Questions

1. 后续是否要把 `qaRetrieval` 的失效粒度从全局清理收敛到知识库级。
2. 是否要为热点问题或热点知识库增加更细的 key 设计。
3. 多实例部署后，当前失效策略是否仍足够稳定和可解释。

## References

1. [README.md](../../README.md)
2. [week3.md](../../rag-backend/work/week3.md)
3. [current-status.md](../../rag-backend/work/current-status.md)
4. [CHANGELOG-20260509.md](../../CHANGELOG-20260509.md)
5. Commit `bab81c2` `Add Redis business caching and close week 3`
6. Commit `3d9cf4d` `Close week 3 review and fix Redis cache config`
7. Commit `764df97` `Fix indexing state consistency and cache regressions`
