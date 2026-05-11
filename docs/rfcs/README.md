# RFC Index

## Purpose

`docs/rfcs/` 用来沉淀项目里已经形成明确工程决策，或者已经进入稳定路线图的主题。

这里的 RFC 不替代：

1. `rag-backend/work/` 里的日记式推进记录。
2. `README.md` 里的项目总览。
3. `rag-backend/work/` 和 `rag-frontend/work/` 里的阶段性推进记录。

这里的 RFC 负责把“分散在周记、日记、提交记录和代码里的长期决策”整理成可持续维护的单点文档。

## Status

当前目录使用下面几种状态：

1. `Planned`
   说明主题已经进入正式路线图，但仓库里还没有完成实现。
2. `Proposed`
   说明主题有明确问题陈述和候选方案，但团队还没有收口最终决定。
3. `Accepted`
   说明决策已经进入当前代码与运行行为。
4. `Superseded`
   说明该 RFC 已被后续 RFC 替代，但为了保留历史仍继续存档。

## Template

新 RFC 默认使用下面结构：

1. Title
2. Status
3. Created / Last Updated
4. Owners
5. Summary
6. Context
7. Decision
8. Historical Evolution
9. Implementation
10. Consequences
11. Non-Goals
12. Open Questions
13. References

不是每份 RFC 都必须完全一致，但至少要能回答三个问题：

1. 为什么要做这个决定。
2. 现在系统到底是怎么做的。
3. 后续如果要改，应该先看哪些历史材料。

## Current RFCs

| RFC | Status | Theme | Why it matters |
| --- | --- | --- | --- |
| [RFC-0001](./RFC-0001-embedding-profile.md) | Accepted | Embedding Profile | 把 embedding 配置从“启动参数”升级为“检索契约 + 重建触发器” |
| [RFC-0002](./RFC-0002-readiness-gate.md) | Accepted | Readiness Gate | 统一 `qa/readiness` 展示状态与真实检索阻断行为 |
| [RFC-0003](./RFC-0003-rerank-pipeline.md) | Planned | Rerank Pipeline | 为后续混合检索和二阶段排序提前占位 |
| [RFC-0004](./RFC-0004-async-indexing-and-recovery.md) | Accepted | Async Indexing And Recovery | 把文档索引从同步动作收敛成可观察、可重试、可恢复的后台任务 |
| [RFC-0005](./RFC-0005-chunking-strategy.md) | Accepted | Chunking Strategy | 为当前固定窗口切块、参数外置和默认 `balanced` 基线建立正式依据 |
| [RFC-0006](./RFC-0006-retrieval-cache-strategy.md) | Accepted | Retrieval Cache Strategy | 为 Redis 读缓存、TTL 和一致性优先的失效策略建立正式依据 |
| [RFC-0007](./RFC-0007-qa-contract-answer-sources-history.md) | Accepted | QA Contract: Answer, Sources And History | 统一问答返回、来源展示和历史回放的证据契约 |
| [RFC-0008](./RFC-0008-knowledge-base-lifecycle.md) | Accepted | Knowledge Base Lifecycle | 明确知识库手工禁用/恢复语义，以及恢复时是否补偿失败索引任务 |

## Candidate Backlog

结合当前仓库里的 `README.md`、`rag-backend/work/` 下周记/日记、前端计划和最近提交记录，下一批最值得进入 RFC 的主题有这些：

### RFC-0009 Evaluation Dataset And Acceptance Baseline

成熟度：中，建议在效果优化前补。

理由：

1. `Day 20` 已经形成中文评测样本文档、问题集、模板和夹具。
2. README 已把 `day20-cn-kb` 结果写进项目完成度。
3. 后续一旦改 chunking、embedding 或 rerank，就需要稳定对比口径。

## Source Map

当前 RFC 目录最主要的历史来源如下：

1. [README.md](../../README.md)
2. [current-status.md](../../rag-backend/work/current-status.md)
3. [week1.md](../../rag-backend/work/week1.md)
4. [week2.md](../../rag-backend/work/week2.md)
5. [week3.md](../../rag-backend/work/week3.md)
6. [frontend plan.md](../../rag-frontend/work/frontend%20plan.md)

这些文档的作用不同：

1. `README.md` 提供当前项目口径和边界。
2. `current-status.md` 与 `week*.md`、`work day*.md` 提供迭代顺序和决策上下文。
3. `frontend plan.md` 提供用户前端接入、运维入口和页面约束。
4. `README.md` 已吸收近期关于 embedding rebuild、知识库恢复和前端构建优化的阶段总结。

## Maintenance Notes

维护这个目录时，建议遵守下面几条：

1. 不把 RFC 写成逐日流水账，历史脉络可以保留，但结论必须先行。
2. 不把“计划”写成“已实现”，尤其是 rerank、混合检索、多轮对话这类主题。
3. 代码实现已经发生变化时，优先更新现有 RFC，而不是先追加一篇重复 RFC。
4. 如果新的变更只是修 bug，而没有改变长期行为，优先写进 README 或周记/日记，不必强行升格为 RFC。
