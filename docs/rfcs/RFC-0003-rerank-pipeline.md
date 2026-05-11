# RFC-0003 Rerank Pipeline

- Status: Planned
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录项目对“重排序 pipeline”的历史定位和后续规划。需要先说明一件事：截至 2026-05-11，仓库里还没有合并完成的 rerank 实现；它目前仍属于路线图事项。本 RFC 的作用是把已有文档里的规划意图整理成正式记录，避免后续讨论重复从零开始。

## Context

当前系统已经具备单阶段 dense retrieval：

1. 为 query 生成 embedding。
2. 在 pgvector 中做 TopK 向量召回。
3. 将召回 chunk 组装进 prompt。
4. 生成最终回答。

这条链路足够支撑最小可用问答，但它也有明显上限：

1. TopK 向量召回不一定等于最终最相关排序。
2. 召回集合里容易混入“语义接近但不回答当前问题”的 chunk。
3. 在中文长文档、多相似片段、需要细粒度证据排序时，单阶段召回的 precision 不够稳定。

项目文档已经多次把“混合检索与重排序”列为后续方向，因此需要一份正式 RFC 说明：这不是已完成能力，而是当前架构的下一阶段扩展点。

## Historical Signals

与 rerank 相关的历史信号主要来自文档，而不是现有代码：

1. [README.md](../../README.md) 明确写到“还没有做混合检索、重排序和更细的召回抑制”。
2. [README.md](../../README.md) 后续方向中再次列出“增加混合检索与重排序”。
3. [work day1.md](../../rag-backend/work/work%20day1.md) 很早就把“重排序”列进能力版图，说明团队从早期就在为它预留概念空间。

换句话说，rerank 不是突然冒出来的新需求，而是历史 backlog 中一直存在、但尚未进入已实现范围的主题。

## Decision

本 RFC 先确立架构方向，而不声称当前已经实现：

1. 当前检索基线仍然是单阶段 dense retrieval。
2. rerank 将被视为二阶段排序层，位置在“召回之后、拼 prompt 之前”。
3. readiness gate 未来需要能表达 rerank 是否可用，但在 rerank 未落地前，不把它纳入现有阻断条件。
4. RFC 编号提前占位，后续实现时沿用同一文档持续演进，而不是另起一份不连续的设计稿。

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

## Expected Benefits

1. 提升证据片段排序质量，减少“召回到了但排位靠后”的问题。
2. 改善相似 chunk 很多时的最终上下文质量。
3. 为后续混合检索提供统一的候选融合与精排层。
4. 为“拒答/低置信度回答”策略提供更可靠的排序信号。

## Dependencies

rerank 真正落地前，至少需要补齐这些决策：

1. 使用独立 rerank 模型，还是复用现有 LLM/embedding 供应商能力。
2. 候选集大小如何控制，避免二阶段排序成本过高。
3. rerank 分数如何与现有向量分数、未来 BM25 分数共同使用。
4. 前端或观测接口是否需要暴露 rerank 命中明细。

当前已有的一些基础工作会直接影响 rerank 的落地质量：

1. `RFC-0001` 中的 embedding profile 会决定 first-stage recall 的向量语义基础。
2. `RFC-0002` 中的 readiness gate 未来可能需要纳入 rerank availability。
3. `Day 19` 的 chunking 实验会直接影响 rerank 候选粒度。
4. `Day 20` 的评测集会成为 rerank 是否真的带来收益的首批验收基线。

因此，rerank 虽然还没实现，但它并不是孤立主题，而是建立在已有检索、切块和评测工作的下一层。

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

## References

1. [README.md](../../README.md)
2. [week2.md](../../rag-backend/work/week2.md)
3. [week3.md](../../rag-backend/work/week3.md)
4. [work day1.md](../../rag-backend/work/work%20day1.md)
5. [work day19.md](../../rag-backend/work/work%20day19.md)
6. [work day20.md](../../rag-backend/work/work%20day20.md)
