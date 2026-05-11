# RFC-0001 Embedding Profile

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录项目 embedding profile 的演进过程，以及当前“embedding 配置即检索契约”的实现方式。核心结论是：embedding provider、base URL、model、path、distance metric、vector dimensions 共同组成一个受控 fingerprint；只要 fingerprint 变化，系统就不再假设旧向量仍然可检索，而是要求进入重嵌入流程。

## Context

项目早期使用本地 OpenAI-compatible embedding 服务，默认模型为 `bge-small-zh-v1.5`，向量维度为 `512`。这一阶段的目标是尽快打通文档切分、向量化、入库和 TopK 检索链路，形成最小可用 RAG。

随着系统进入多周迭代，embedding 配置开始不再只是启动参数，而是直接影响：

1. 已入库向量是否还能参与检索。
2. pgvector 列的维度约束是否仍然匹配。
3. readiness 页面与真实检索入口是否能保持一致。
4. 历史知识库在切换模型后是否需要整体重建。

在切换到阿里云百炼兼容接口 `text-embedding-v4` 后，上述问题变成真实问题，而不是理论风险。

## Decision

系统当前将以下字段视为 embedding profile 的组成部分：

1. `provider`
2. `baseUrl`
3. `model`
4. `embeddingPath`
5. `distanceMetric`
6. `vectorDimensions`

这些字段组合后形成 fingerprint，并作为“当前活动 embedding 配置”的识别依据。`apiKey` 不参与 fingerprint 计算，因为它只影响鉴权，不影响向量语义或存储结构。

当前目标 profile 为：

1. `provider=aliyun-bailian-openai-compatible`
2. `baseUrl=https://dashscope.aliyuncs.com/compatible-mode/v1`
3. `model=text-embedding-v4`
4. `embeddingPath=/embeddings`
5. `distanceMetric=cosine`
6. `vectorDimensions=1024`

当系统检测到历史数据来自旧 profile，或发现当前活动数据与配置维度不一致时，不允许继续把旧向量当作“可安全检索”的数据使用，而是要求重嵌入。

## Historical Evolution

### Phase 1: 本地 embedding 打通最小链路

- 对应提交：`5185f87` `Add local embedding pipeline and day 9 docs`
- 特征：本地 embedding 服务、`bge-small-zh-v1.5`、`512` 维、先解决“能入库、能召回”。

### Phase 2: 检索链路稳定后，embedding profile 的隐性约束暴露

- 对应提交：`29507a6` `implement day10 retrieval and prepare day11`
- 特征：系统已经依赖 query embedding 与 chunk embedding 的一致性，embedding 配置开始影响真实召回结果。

### Phase 3: 切换到远端兼容接口，profile 升级为系统级状态

- 参考材料：`README.md` 最近补充、`week2.md`、`current-status.md`
- 特征：模型切换到 `text-embedding-v4`，维度升到 `1024`，旧数据不再能默认视为安全。

### Phase 4: 引入 profile 状态识别与重建编排

- 当前实现：`EmbeddingConfigurationStateService`
- 特征：系统识别 legacy active embeddings、标记 `reembedRequired`、驱动异步 rebuild、阻断未完成迁移的检索流量。

## Implementation

当前实现位于 [EmbeddingConfigurationStateService.java](../../rag-backend/src/main/java/com/example/rag/service/EmbeddingConfigurationStateService.java)。

该实现负责：

1. 生成当前配置 fingerprint。
2. 识别历史 legacy fingerprint。
3. 判断知识库是否需要 `reembedRequired`。
4. 为 readiness 和 rebuild 流程提供统一状态来源。

异步重建流程位于 [EmbeddingRebuildService.java](../../rag-backend/src/main/java/com/example/rag/service/EmbeddingRebuildService.java)，其职责是将 profile 变化从“配置变更”推进到“数据重建完成”。

真实联调中还沉淀了两条关键修正：

1. DashScope `text-embedding-v4` 批量上限按 `10` 处理，相关逻辑位于 [DocumentEmbeddingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentEmbeddingService.java)。
2. 旧 `ivfflat` 向量索引会阻止 `1024` 维向量写入，因此通过 [V15__drop_legacy_vector_index.sql](../../rag-backend/src/main/resources/db/migration/V15__drop_legacy_vector_index.sql) 清理旧索引约束。

重建状态持久化通过 [V14__embedding_rebuild_state.sql](../../rag-backend/src/main/resources/db/migration/V14__embedding_rebuild_state.sql) 落地。

## Consequences

正面影响：

1. embedding 配置变化不再悄悄污染检索结果。
2. readiness、检索入口和重建任务共享同一套状态语义。
3. 维度切换从“人工记忆事项”变成“系统显式状态”。

代价与约束：

1. 每次 profile 发生语义级变化，历史向量都需要重建。
2. 删除 legacy 索引后，系统暂时优先保证正确性，后续还需要重新设计适配当前 profile 的向量索引。
3. embedding provider 的兼容层行为不能只看 OpenAI 风格接口表面一致，仍需实测批量限制和维度返回值。

## Validation And Incidents

这个主题之所以值得单独立 RFC，不只是因为“有设计”，而是因为已经发生过真实迁移事故和修正：

1. 旧 `512` 维向量在首次迁移后没有自动进入 `reembedRequired`，暴露出 profile 状态识别不能依赖人工约定。
2. DashScope `text-embedding-v4` 的真实批量上限需要按 `10` 控制，说明兼容协议并不等于行为完全一致。
3. 旧 `ivfflat` 索引会直接拒绝 `1024` 维写入，说明向量存储结构本身也是 embedding profile 的一部分约束。

因此，embedding profile 在当前项目里已经不是“配置说明”，而是一个带有运维和迁移语义的工程边界。

## Non-Goals

本 RFC 不定义：

1. 多 embedding profile 并存检索。
2. 不同知识库使用不同 embedding model 的路由策略。
3. 新一版向量索引的最终设计。

## Open Questions

1. 后续是否允许单知识库级别的 profile pinning，而不是全局单 profile。
2. 是否需要为 profile fingerprint 引入版本号或可读标签，降低排障成本。
3. 新向量索引应按“同维度”还是“同 fingerprint”建立。

## References

1. [README.md](../../README.md)
2. [week2.md](../../rag-backend/work/week2.md)
3. [current-status.md](../../rag-backend/work/current-status.md)
4. [work day9.md](../../rag-backend/work/work%20day9.md)
5. [work day10.md](../../rag-backend/work/work%20day10.md)
6. Commit `5185f87` `Add local embedding pipeline and day 9 docs`
7. Commit `29507a6` `implement day10 retrieval and prepare day11`
