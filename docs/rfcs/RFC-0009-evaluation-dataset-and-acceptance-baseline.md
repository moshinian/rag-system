# RFC-0009 Evaluation Dataset And Acceptance Baseline

- Status: Accepted
- Created: 2026-05-12
- Last Updated: 2026-05-14
- Owners: RAG Team

## Summary

本 RFC 记录当前中文问答评测集、验收口径和真实结果。核心结论是：系统已经不再只靠“主观感觉回答还行”来判断质量，而是固定一套最小可用的评测基线，包括中文样本文档、问题集、观察维度、结果模板和测试夹具；Week 4 之后，这套基线还扩展成 `DENSE vs HYBRID` 的双轨对比口径，用来判断混合检索是否真的带来净收益。

## Context

到 Week 3 后半段，系统已经具备：

1. 异步索引、失败重试和恢复。
2. 结构化日志与配置外置。
3. 固定窗口切块和参数对比实验。
4. 检索、问答、来源和历史回放。

此时如果继续只凭单次演示或个别问题判断效果，会遇到几个典型问题：

1. 检索命中和最终回答混在一起，无法判断到底是哪一层退化。
2. 调 chunk 参数、embedding 模型或 prompt 后，没有稳定对照组。
3. 无答案问题是否稳定拒答，很容易被“看起来没问题”的演示掩盖。
4. README、周记、真实运行结果和测试夹具容易逐步脱节。

因此 Day 20 没有继续扩功能，而是先把“评测样本、问题集、结果模板、验证维度和首轮结果”固定下来。

## Decision

系统当前采用一版最小可用的中文问答评测基线，具体约定如下：

1. 评测语言固定为 `zh-CN`。
2. 第一版评测知识库固定为 `day20-cn-kb`。
3. 默认检索参数固定为 `topK=3`。
4. 评测问题覆盖四类：
   - `FACT`
   - `SUMMARY`
   - `PROCESS`
   - `NO_ANSWER`
5. 每条 case 至少记录：
   - `caseCode`
   - `category`
   - `question`
   - `expectedDocument`
   - `expectedKeywords`
   - `expectationType`
   - `notes`
6. 每轮结果至少记录：
   - `retrievalHit`
   - `answerAcceptable`
   - `sourceStable`
   - `notes`

Week 4 之后，dense vs hybrid 对比口径进一步固定为：

1. `denseRetrievalHit`
2. `hybridRetrievalHit`
3. `denseAnswerAcceptable`
4. `hybridAnswerAcceptable`
5. `denseSourceStable`
6. `hybridSourceStable`
7. `notes`

这套基线当前不是“最终评分体系”，而是第一版验收底线。它的目标是让后续效果改动具备可重复、可回看、可对比的最小依据。

## Historical Evolution

### Phase 1: 先做最小问答闭环

- 时间：Week 2
- 特征：系统已经具备检索、问答、来源返回和历史记录，但还没有正式评测基线。

### Phase 2: 先固定 chunk 参数和工程边界

- 时间：Week 3 前半段
- 特征：异步索引、缓存、日志、切块实验已具备，开始有条件对问答效果做稳定比较。

### Phase 3: 建立 Day 20 中文评测基线

- 时间：Day 20
- 特征：
  - 新增三份中文样本文档。
  - 新增问题集 `day20-qa-eval-cases.json`。
  - 新增结果模板和结果记录。
  - 新增数据完整性测试 `QaEvaluationDatasetTest`。
  - 新增真实检索评测夹具 `QaRetrievalEvaluationIntegrationTest`。
  - 完成 `day20-cn-kb` 首轮真实问答评测。

### Phase 4: 扩展成 Day 25 / Day 26 的 dense vs hybrid 双轨评测

- 时间：Week 4
- 特征：
  - 新增双轨问题集 `day25-hybrid-eval-cases.json`
  - 新增结果模板和执行 runbook
  - 新增 Day 26 补充样本 `day26-hybrid-supplemental-cases.json`
  - 完成两轮真实 `DENSE vs HYBRID` 对比，并把“收益集中在关键词密集题型”写成正式结论

## Implementation

当前评测基线由下面几部分组成：

1. 本 RFC 记录的固定口径、结果结论和样本维度说明。
2. 数据完整性校验：
   [QaEvaluationDatasetTest.java](../../rag-backend/src/test/java/com/example/rag/evaluation/QaEvaluationDatasetTest.java)
3. 真实检索评测夹具：
   [QaRetrievalEvaluationIntegrationTest.java](../../rag-backend/src/test/java/com/example/rag/evaluation/QaRetrievalEvaluationIntegrationTest.java)
4. [week3.md](../work/rag-backend/week3.md) 与 [work day20.md](../work/rag-backend/work%20day20.md) 对 Day 20 基线的阶段收口。
5. [work day25.md](../work/rag-backend/work%20day25.md) 与 [work day26.md](../work/rag-backend/work%20day26.md) 对 dense vs hybrid 双轨评测的真实记录。

说明：

1. 早期评测 JSON / Markdown 明细文件当前未保留在 `docs/work` 目录中。
2. 当前仓库里可持续维护的“评测真相源”已经收口为：本 RFC、测试夹具以及对应的 Week / Day 工作记录。

当前数据集的固定事实包括：

1. `kbCode = day20-cn-kb`
2. `language = zh-CN`
3. `topK = 3`
4. 共 `6` 条 case
5. 覆盖四类问题：`FACT / SUMMARY / PROCESS / NO_ANSWER`

当前结果记录中的首轮基线结论是：

1. `5/5` 可回答问题命中预期文档。
2. `5/5` 可回答问题的最终回答可接受。
3. `5/5` 可回答问题的来源与回答一致。
4. `1/1` 无答案问题返回兜底话术，没有直接胡乱编造。
5. 当前主要剩余问题不在回答生成，而在无答案场景下检索仍会返回弱相关 chunk。

Week 4 的真实 dense vs hybrid 结论则进一步补成：

1. 基线样本上，`DENSE` 与 `HYBRID` 整体结果接近，没有出现足够大的净收益。
2. 补充样本上，`HYBRID` 在关键词密集、ASCII term、document lookup 题型上出现明确净收益。
3. 当前收益已经足以证明第一版 hybrid 值得保留，但还不足以支持把默认模式切到 `HYBRID`。

2026-05-14 又补做了一次运行时验证，但这次不是切 `DENSE/HYBRID`，而是切 `HYBRID` 内部的 lexical strategy：

1. 默认 `LIKE` 路径在当前样本上仍能稳定跑通真实 `retrieve / ask`。
2. 新增的 `POSTGRES_FTS` 路径已经完成真实启动、真实请求和前端代理联调。
3. 但在当前 `day20-cn-kb` 中文样本与 mixed-term 问句上，`POSTGRES_FTS(simple)` 没有观察到稳定的 `keywordHitCount` 提升。
4. 对“第二百三十八条的”这类中文条文短语，真实使用中还观察到了 `LIKE` 能命中、`POSTGRES_FTS` 零命中的情况，说明当前 PostgreSQL 默认词法处理对中文法规短语仍然偏弱。
5. 为避免这种体验倒挂，系统现已在 `POSTGRES_FTS` 的 CJK 零命中场景下自动回退到 `LIKE` keyword recall。
6. 因此当前评测基线仍以默认 `LIKE` 版 hybrid 作为真实对照口径，而不是把 `POSTGRES_FTS` 直接写进默认验收结论。

## Why This Baseline Is Intentionally Small

系统当前没有一上来就做复杂自动评分，是刻意控制范围。

原因是：

1. 项目还在第一版工程化收口阶段，先需要稳定口径，再需要规模。
2. 当前最有价值的是“防止明显退化”，而不是追求虚假的细粒度分数。
3. `retrievalHit / answerAcceptable / sourceStable` 已经足以把主要问题拆分到检索层、生成层和来源层。
4. 真实中文样本文档和真实知识库比纯合成 case 更适合这个阶段。

## Consequences

正面影响：

1. 评测第一次从口头描述变成仓库内资产。
2. README、状态文档和真实样本之间有了共同基线。
3. 后续优化 chunking、embedding、prompt 或 rerank 时，有最低限度的回归对照。
4. 无答案问题第一次被纳入正式验收，而不是只看“可回答问题”。

代价与约束：

1. 当前 case 数量还小，只能算第一版验收底线。
2. `answerAcceptable` 和 `sourceStable` 仍然是半人工判断，不是完全自动评分。
3. 真正的端到端检索评测夹具依赖数据库和 embedding 服务，本地默认仍需受环境约束。
4. 数据集、样本文档和结果记录一旦漂移，就会导致 RFC 与测试夹具脱节。

## Non-Goals

本 RFC 不定义：

1. 最终版自动评分体系。
2. 复杂的多维 benchmark 平台。
3. 多轮对话评测。
4. rerank 的最终验收标准。

## Open Questions

1. 后续是否要把 `answerAcceptable` 和 `sourceStable` 进一步拆成更细维度。
2. 无答案场景是否需要单独增加更多 case，避免当前只靠 `1` 条问题判断。
3. 后续引入 rerank 或 citation mapping 后，是否需要新增 `rerankGain` 或 `citationCorrectness` 维度。
4. 评测结果应继续以 Markdown 记录为主，还是补一份更结构化的结果文件。

## References

1. [README.md](../../README.md)
2. [RFC Index](./README.md)
3. [week3.md](../work/rag-backend/week3.md)
4. [current-status.md](../work/rag-backend/current-status.md)
5. [work day20.md](../work/rag-backend/work%20day20.md)
6. [QaEvaluationDatasetTest.java](../../rag-backend/src/test/java/com/example/rag/evaluation/QaEvaluationDatasetTest.java)
7. [QaRetrievalEvaluationIntegrationTest.java](../../rag-backend/src/test/java/com/example/rag/evaluation/QaRetrievalEvaluationIntegrationTest.java)
8. [work day25.md](../work/rag-backend/work%20day25.md)
9. [work day26.md](../work/rag-backend/work%20day26.md)
