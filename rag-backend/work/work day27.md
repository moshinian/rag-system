# Day 27：补齐 Hybrid Retrieval 的关键日志与最小指标口径

## 今日目标

Day 26 已经把 Week 4 最关键的效果验证补完了：

1. 第一轮基线样本已经完成真实 `DENSE vs HYBRID` 对比
2. 第二轮补充样本已经确认 hybrid 在关键词密集题型上存在明确收益

所以 Day 27 的重点不再是继续证明“有没有收益”，而是：

**把这条已经验证过价值的 hybrid retrieval 主链路，补成一条可排障、可解释、可继续调优的工程链路。**

今天要解决的问题是：

1. 一次 `DENSE` 或 `HYBRID` 请求到底慢在哪里；
2. 一次回答质量异常时，问题更可能出在 dense、keyword、fusion 还是 LLM；
3. Day 26 的评测结论如何和运行时日志、耗时字段对得上。

## 为什么 Day 27 做这个

Week 4 到 Day 26 为止，系统已经具备：

1. `dense recall + keyword recall + RRF fusion`
2. `qa/retrieve / qa/ask / qa/history` 的统一 retrieval 契约
3. 两轮真实 `DENSE vs HYBRID` 对比评测

但当前仍有一个明显缺口：

**效果结论已经有了，运行时观测还不够完整。**

如果 Day 27 不补这一步，后面会出现几个问题：

1. 知道 `HYBRID` 在哪些题型上更好，却不知道代价是不是主要出在 keyword recall 或 fusion；
2. 知道某题答坏了，却不能快速区分是 retrieval 排序问题，还是 LLM 组织问题；
3. Day 26 的评测结果可以写进文档，但很难映射到真实线上排障口径。

所以 Day 27 的任务不是再扩评测，而是：

**把 Day 26 的结论接到真实可观测性上。**

## Day 27 要完成什么

### 1. 检索链路关键日志补齐

至少需要确保下面这些事件稳定存在，并字段足够解释问题：

1. `qa.retrieve.started`
2. `qa.retrieve.dense.completed`
3. `qa.retrieve.keyword.completed`
4. `qa.retrieve.fusion.completed`
5. `qa.retrieve.completed`

### 2. 问答链路关键日志补齐

至少需要确保：

1. `qa.ask.started`
2. `qa.ask.llm.completed`
3. `qa.ask.completed`

这样才能把 retrieval 与 LLM 两层耗时拆开，而不是只看到一次总耗时。

### 3. 最小指标字段收口

Day 27 不要求接完整 metrics 平台，但至少要把 Week 4 最小字段口径补齐：

1. `retrievalMode`
2. `denseCandidateCount`
3. `keywordCandidateCount`
4. `finalHitCount`
5. `denseDurationMs`
6. `keywordDurationMs`
7. `fusionDurationMs`
8. `llmDurationMs`
9. `totalDurationMs`

### 4. 与 Day 26 评测结论对齐

Day 27 结束时，至少要能解释：

1. 为什么补充样本里 `HYBRID` 更强；
2. 这种收益主要伴随了哪一段耗时增加；
3. 当前默认模式为什么仍然先保留 `DENSE`。

## Day 27 的边界

今天明确不做：

1. 不补新的 retrieval 算法
2. 不继续扩大量评测样本
3. 不做完整监控大盘
4. 不切默认模式

今天只做关键日志、最小指标和解释口径收口。

## Day 27 的输出物

今天完成后，至少应该留下：

1. 一版完整的 hybrid retrieval 子阶段日志
2. 一版最小可用的耗时字段口径
3. 一版可用于 README / Week 4 收口的观测结论
