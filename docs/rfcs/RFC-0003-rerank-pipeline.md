# RFC-0003 Rerank Pipeline

- Status: Planned
- Created: 2026-05-11
- Last Updated: 2026-05-14
- Owners: RAG Team

## Summary

本 RFC 记录项目对“重排序 pipeline”的历史定位和后续规划。需要先说明一件事：截至 2026-05-14，仓库里仍然没有合并完成的 rerank 实现；它目前仍属于路线图事项。Week 4 已经完成第一版 hybrid retrieval，但 hybrid 不等于 rerank。本 RFC 的作用是把已有文档里的规划意图整理成正式记录，同时明确“当前没有做什么”，避免后续把路线图误读成现状。

## Context

当前系统已经具备第一版 hybrid retrieval 主链路：

1. 为 query 生成 embedding。
2. 在 pgvector 中做 dense recall。
3. 基于 PostgreSQL 数据做 keyword recall。
4. 用 `RRF` 做 first-stage fusion。
5. 将融合后的 chunk 组装进 prompt。
6. 生成最终回答。

这条链路足够支撑最小可用问答，但它也有明显上限：

1. TopK 向量召回不一定等于最终最相关排序。
2. 召回集合里容易混入“语义接近但不回答当前问题”的 chunk。
3. 在中文长文档、多相似片段、需要细粒度证据排序时，单阶段召回的 precision 不够稳定。

项目文档已经多次把“混合检索与重排序”列为后续方向，因此需要一份正式 RFC 说明：这不是已完成能力，而是当前架构的下一阶段扩展点。

## Current State Boundary

为了避免规划文档和当前实现混淆，本 RFC 先把现状边界写死：

1. 当前线上口径已经支持 `DENSE / HYBRID` 两种 first-stage retrieval，但仍然没有二阶段 rerank。
2. 当前 `POST /qa/retrieve`、`POST /qa/ask`、`sources`、`/qa/history` 都没有暴露 rerank 分数、候选重排结果或 rerank explain 字段。
3. 当前 readiness gate 不检查 rerank 模型可用性，也不会因为 rerank 缺失而阻断问答。
4. 当前评测基线仍以“无 rerank”链路作为真实验收口径。
5. 当前 README 的准确口径应当理解为“混合检索已完成第一版实现，但 rerank 与更细的召回抑制仍未开始”。

换句话说，只要仓库中还没有出现独立 rerank 排序层、可观测结果和新的验收口径，就不能把任何 retrieval 效果变化表述成“rerank 已上线”。

## Historical Signals

与 rerank 相关的历史信号主要来自文档，而不是现有代码：

1. [work day1.md](../../rag-backend/work/work%20day1.md) 很早就把“重排序”列进能力版图，说明团队从早期就在为它预留概念空间。
2. [work day22.md](../../rag-backend/work/work%20day22.md) 已明确 Week 4 先固定为 `dense + keyword + RRF`，并刻意把 rerank 留在 RFC 规划范围内。
3. [README.md](../../README.md) 现已明确写到：Week 4 完成的是 hybrid retrieval，而不是 rerank。

换句话说，rerank 不是突然冒出来的新需求，而是历史 backlog 中一直存在、但尚未进入已实现范围的主题。

## Decision

本 RFC 先确立架构方向，而不声称当前已经实现：

1. 当前检索基线已经升级为可切换的 first-stage retrieval（`DENSE / HYBRID`），但 rerank 仍未进入主链路。
2. rerank 将被视为二阶段排序层，位置在“召回之后、拼 prompt 之前”。
3. readiness gate 未来需要能表达 rerank 是否可用，但在 rerank 未落地前，不把它纳入现有阻断条件。
4. RFC 编号提前占位，后续实现时沿用同一文档持续演进，而不是另起一份不连续的设计稿。
5. 在真正进入开发前，必须先补齐“模型来源、候选规模、降级行为、评测口径”四个前置决定，避免实现阶段边写边猜。

## Proposed Pipeline

建议中的两阶段链路如下：

1. Query preprocessing
2. First-stage recall
3. Candidate truncation
4. Second-stage rerank
5. Prompt assembly
6. Answer generation

在不改变现有主链路的前提下，rerank 更适合作为可开关、可降级的增强层：

1. rerank 不可用时，系统退回当前 TopK 结果。
2. rerank 可用时，对召回候选重新打分，输出更精确的上下文顺序。

## Adoption Trigger

只有满足下面条件，rerank 才应该从 `Planned` 进入真正实现阶段：

1. 已经确定 rerank 能力来源，是独立模型、供应商接口还是本地服务。
2. 已经确定 first-stage recall 的候选截断规模，并能解释成本上界。
3. 已经确定“失败时如何降级回当前 dense retrieval”，且不会把现有问答主链路变成强依赖。
4. 已经确定至少一套可重复的验收口径，能够用 `RFC-0009` 的评测基线证明 rerank 带来净收益，而不是只凭主观观感。
5. 已经确定对外契约策略，明确是先内部启用、还是同步暴露 rerank 信号给前端和历史记录。

## Expected Benefits

1. 提升证据片段排序质量，减少“召回到了但排位靠后”的问题。
2. 改善相似 chunk 很多时的最终上下文质量。
3. 为当前 hybrid retrieval 提供统一的二阶段精排层，而不是继续把 first-stage fusion 误当成最终排序。
4. 为“拒答/低置信度回答”策略提供更可靠的排序信号。

## Dependencies

rerank 真正落地前，至少需要补齐这些决策：

1. 使用独立 rerank 模型，还是复用现有 LLM/embedding 供应商能力。
2. 候选集大小如何控制，避免二阶段排序成本过高。
3. rerank 分数如何与现有 dense recall、keyword recall 与 `RRF fusion` 结果衔接。
4. 前端或观测接口是否需要暴露 rerank 命中明细。

当前已有的一些基础工作会直接影响 rerank 的落地质量：

1. `RFC-0001` 中的 embedding profile 会决定 first-stage recall 的向量语义基础。
2. `RFC-0002` 中的 readiness gate 未来可能需要纳入 rerank availability。
3. `Day 19` 的 chunking 实验会直接影响 rerank 候选粒度。
4. `Day 20` 到 `Day 26` 的评测集会成为 rerank 是否真的带来收益的首批验收基线。

因此，rerank 虽然还没实现，但它并不是孤立主题，而是建立在已有检索、切块和评测工作的下一层。

## Consequences

当前把 rerank 保持在 `Planned` 状态，有两个直接后果：

1. 正面影响：团队不会把“潜在优化方向”误写成“现有产品能力”，README、RFC 和真实代码口径能保持一致。
2. 代价：短期内问答质量提升仍然主要依赖切块、embedding profile、prompt 和评测收敛，而不是二阶段排序。

这是一种刻意的保守做法。先把现有 dense retrieval 基线、评测基线和证据契约站稳，再引入 rerank，工程风险更低。

## Non-Goals

本 RFC 当前不定义：

1. 具体 rerank 模型选型。
2. 混合检索中 BM25 的接入方案。
3. 最终 API 字段和响应格式。
4. 评测基准的最终口径。

## Open Questions

1. rerank 是否应该只用于问答入口，还是同时覆盖纯检索入口。
2. 候选集是固定 TopN，还是按相似度阈值截断。
3. 若 rerank 成本较高，是否需要按知识库规模、租户或请求类型做动态开关。
4. readiness 接口未来要不要新增 `rerankEnabled`、`rerankAvailable` 等字段。
5. 如果后续同时推进 session reuse 和多轮问答，rerank 是否需要感知会话上下文，而不只是当前单轮 query。

## References

1. [README.md](../../README.md)
2. [week2.md](../../rag-backend/work/week2.md)
3. [week3.md](../../rag-backend/work/week3.md)
4. [work day1.md](../../rag-backend/work/work%20day1.md)
5. [work day19.md](../../rag-backend/work/work%20day19.md)
6. [work day20.md](../../rag-backend/work/work%20day20.md)
