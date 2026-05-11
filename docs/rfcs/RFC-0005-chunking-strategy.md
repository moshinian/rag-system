# RFC-0005 Chunking Strategy

- Status: Accepted
- Created: 2026-05-11
- Last Updated: 2026-05-11
- Owners: RAG Team

## Summary

本 RFC 记录当前固定窗口切块策略、参数外置方式，以及为什么默认值保留在 `balanced(600/80/240)`。核心结论是：当前项目优先选择“简单、可解释、可重复实验”的固定窗口切块，而不是一开始就引入复杂语义切块；在现有样本下，`balanced` 在召回粒度、chunk 数量和单块长度之间提供了最稳妥的折中。

## Context

在当前 RAG 系统里，chunking 不是单纯的数据预处理细节，而是直接影响：

1. 每篇文档会被拆成多少候选片段。
2. TopK 检索时的召回粒度。
3. prompt 拼接时每块上下文的噪声水平。
4. 后续评测结果能否稳定复现。

项目早期已经先做出最小可用的固定长度切块，但到了 Week 3，系统需要的不再只是“能切”，而是：

1. 参数可配置。
2. 默认值有依据。
3. 调参有实验记录，而不是凭感觉。

## Decision

系统当前采用固定窗口切块 `FixedWindowChunker`，并将下面三类参数外置为 `rag.chunking.*`：

1. `strategy`
2. `maxChunkChars`
3. `overlapChars`
4. `minBreakSearchOffset`

当前默认值为：

1. `strategy=fixed-window-balanced`
2. `maxChunkChars=600`
3. `overlapChars=80`
4. `minBreakSearchOffset=240`

默认 profile 对应团队内部约定的 `balanced(600/80/240)`。

## Historical Evolution

### Phase 1: 先落地最小可用固定窗口切块

- 相关背景：Week 1 文档入库主链路
- 特征：优先保证 `md / txt / pdf` 都能切块入库，不追求复杂策略。

### Phase 2: 切块成为后续检索和问答的直接前提

- 相关背景：Week 2 检索和问答主链路完成
- 特征：chunk 长度和 overlap 已经开始影响召回质量和 prompt 质量。

### Phase 3: 参数外置，为实验做准备

- 相关提交：`81ff63d` `complete day18 config externalization and day19 chunking experiment`
- 特征：切块参数从代码常量迁移到配置，`DocumentProcessingService` 同时记录切块元数据。

### Phase 4: 对比实验形成默认值依据

- 相关提交：`686c48d` `fix chunking experiment null-safety and add day20 plan`
- 特征：`compact / balanced / wide` 三组参数在同一批样本上完成可重复实验。

## Implementation

当前核心实现位于：

1. [FixedWindowChunker.java](../../rag-backend/src/main/java/com/example/rag/ingestion/chunk/FixedWindowChunker.java)
2. [RagChunkingProperties.java](../../rag-backend/src/main/java/com/example/rag/config/RagChunkingProperties.java)
3. [DocumentProcessingService.java](../../rag-backend/src/main/java/com/example/rag/service/DocumentProcessingService.java)

当前策略要点如下：

1. 以最大字符数作为窗口上限。
2. 在窗口尾部优先寻找更自然的断点，而不是机械硬切。
3. 使用固定 overlap 保留跨块上下文连续性。
4. 在 chunk 元数据中记录当前策略名和 overlap 参数，便于回溯历史处理结果。

## Experiment Baseline

当前默认值不是拍脑袋选出来的，而是来自 [ChunkingExperimentTest.java](../../rag-backend/src/test/java/com/example/rag/ingestion/chunk/ChunkingExperimentTest.java) 的对比实验。

实验使用三组参数：

1. `compact = 480 / 60 / 180`
2. `balanced = 600 / 80 / 240`
3. `wide = 720 / 120 / 300`

实验样本覆盖：

1. `day4-upload-sample.md`
2. `day4-upload-sample.txt`
3. `day4-upload-sample.pdf`
4. `day19-chunking-sample.md`

在当前样本下，结论是：

1. `compact` 总 chunk 数最多，为 `15`。
2. `balanced` 总 chunk 数为 `14`，比 `compact` 少 `1`。
3. `wide` 总 chunk 数降到 `10`，单块明显更大。
4. 长 Markdown 样本中，`balanced` 已出现 `4` 个 `>500` 字符 chunk，`wide` 出现 `5` 个。
5. 当前阶段如果优先考虑召回粒度与稳健性，`balanced` 是最合适的折中方案。

## Why Not Semantic Chunking Yet

当前没有直接引入语义切块、标题树切块或模型辅助切块，原因很现实：

1. Week 1 目标是先把文档入库主链路跑通。
2. Week 2 和 Week 3 更需要可解释、可重复、可快速验证的策略。
3. 如果连固定窗口参数都没有实验基线，过早引入更复杂策略会让问题来源更难定位。

因此，当前项目先把“固定窗口切块 + 参数外置 + 实验记录”做稳，再考虑更复杂的切块语义。

## Relationship To Other Decisions

这个主题与其他 RFC 存在直接依赖关系：

1. `RFC-0001` 的 embedding profile 会影响相同 chunk 在向量空间中的表达质量。
2. `RFC-0003` 的 rerank 候选粒度会直接受 chunk 切法影响。
3. `Day 20` 的中文问答评测是在固定当前默认 chunking 参数后执行的。

换句话说，chunking 是当前检索效果链路里的上游决策，而不是局部实现细节。

## Validation

当前已有直接验证材料：

1. [DocumentProcessingServiceTest.java](../../rag-backend/src/test/java/com/example/rag/service/DocumentProcessingServiceTest.java) 验证切块配置已经真实影响处理行为。
2. [ChunkingExperimentTest.java](../../rag-backend/src/test/java/com/example/rag/ingestion/chunk/ChunkingExperimentTest.java) 验证三组 profile 的统计差异和基本排序关系。
3. [work day18.md](../../rag-backend/work/work%20day18.md) 记录了参数外置背景。
4. [work day19.md](../../rag-backend/work/work%20day19.md) 记录了实验方法与第一版结论。

## Consequences

正面影响：

1. 切块参数已经从代码常量升级为可调配置。
2. README 和默认值开始有实验依据支撑。
3. 后续做 embedding、retrieval、rerank 调优时，有一个稳定的 chunking 基线。

代价与约束：

1. 固定窗口切块依然不理解文档语义结构。
2. 当前实验样本规模有限，`balanced` 只是现阶段最稳妥的默认值，不代表全局最优。
3. 一旦默认值变化，历史知识库的 chunking 基线和评测结果都需要重新解释。

## Non-Goals

本 RFC 不定义：

1. 语义切块算法。
2. 基于标题层级的结构化切块。
3. token 级而不是字符级的精确切块。
4. 不同知识库使用不同切块 profile 的路由机制。

## Open Questions

1. 后续是否需要把 chunking profile 升级为知识库级配置，而不是全局默认。
2. 是否需要把评测集继续扩大到更长、更复杂的中文文档，以验证 `balanced` 是否仍然成立。
3. 当 rerank 落地后，最佳 chunk 大小是否会发生变化。

## References

1. [README.md](../../README.md)
2. [week1.md](../../rag-backend/work/week1.md)
3. [week2.md](../../rag-backend/work/week2.md)
4. [week3.md](../../rag-backend/work/week3.md)
5. [current-status.md](../../rag-backend/work/current-status.md)
6. [work day18.md](../../rag-backend/work/work%20day18.md)
7. [work day19.md](../../rag-backend/work/work%20day19.md)
8. Commit `81ff63d` `complete day18 config externalization and day19 chunking experiment`
9. Commit `686c48d` `fix chunking experiment null-safety and add day20 plan`
