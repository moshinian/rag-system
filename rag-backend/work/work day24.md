# Day 24：打通 `qa/retrieve` 与 `qa/ask` 的 Hybrid 主链路

## 今日目标

Day 23 已经把 hybrid retrieval 的内核落下去了，Day 24 的重点不再是继续补召回算法，而是：

**把已经跑起来的 retrieval 内核完整接入 `qa/retrieve` 与 `qa/ask` 的对外主链路。**

今天要解决的不是“能不能做 hybrid retrieval”，而是：

1. 当前接口契约是否已经足够稳定；
2. `qa/retrieve` 与 `qa/ask` 是否已经共享同一套 hybrid 结果；
3. 当前返回结构是否足以支撑后续评测和前端联调。

## 为什么 Day 24 做这个

Day 23 已经完成：

1. `RetrievalMode`
2. PostgreSQL 内第一版 keyword retrieval
3. 第一版 `RRF fusion`
4. retrieval cache key 的模式隔离

但仅做到这一步还不够，因为如果接口层没有收口，后面会出现两个问题：

1. `qa/retrieve` 和 `qa/ask` 可能各自带着不同口径往前演化；
2. 评测和前端无法稳定知道当前一次请求到底跑的是 dense 还是 hybrid。

所以 Day 24 的价值在于：

**把 Day 23 的检索内核升级成真正可对外使用、可观察、可继续评测的主链路能力。**

## Day 24 要完成什么

### 1. `qa/retrieve` 对外契约收口

今天需要确认 `POST /qa/retrieve` 的返回已经具备最小可用信息：

1. `retrievalMode`
2. `fusionStrategy`
3. `denseHitCount`
4. `keywordHitCount`
5. 最终 `chunks`

这部分的重点不是继续扩字段，而是确认：

1. dense-only 和 hybrid 的语义已经可区分；
2. 字段含义已经稳定；
3. 不需要依赖日志才能知道一次请求跑了什么。

### 2. `qa/ask` 主链路收口

今天还需要确认 `QaService.ask()` 对 retrieval 的复用已经彻底打通：

1. `qa/ask` 不再绕开 retrieval mode；
2. `QaAnswerResponse` 中的 `retrievalResults` 始终来自最终 fusion 结果；
3. `sources` 继续从最终结果映射，不重新造另一套来源口径。

这一步的意义在于：

后续评测看到的回答、来源和检索结果，必须来自同一条链路，而不是三套互相靠猜对齐的数据。

### 3. history 兼容策略定清楚

Day 24 不一定需要把 history 一次做完，但至少要把策略定清楚：

1. 当前历史回放是否需要体现 `retrievalMode`
2. 如果需要，是先放进快照 JSON，还是先新增字段
3. 哪些内容可以放到 Day 25 以后，哪些内容今天必须先定口径

如果这一步不先明确，后面一旦开始做真实评测回看，就会出现“当时到底跑的是 dense 还是 hybrid”说不清的问题。

### 4. 最小验证路径补齐

今天至少需要形成一条稳定验证路径：

1. 指定 `retrievalMode=DENSE`
2. 指定 `retrievalMode=HYBRID`
3. 观察 `qa/retrieve` 返回差异
4. 观察 `qa/ask` 返回中的 `retrievalResults / sources` 是否保持一致

Day 24 不要求完成最终评测，但至少要为 Day 25 和 Day 26 的评测提供可重复入口。

## Day 24 的边界

今天明确不做下面这些事：

1. 不补大规模评测数据集
2. 不写最终 Week 4 评测结论
3. 不做完整 metrics 平台接入
4. 不做前端大范围交互改版
5. 不引入 rerank

今天只做接口主链路收口和最小验证路径打通。

## Day 24 的验收口径

Day 24 结束时，至少要能回答清楚下面几个问题：

1. `qa/retrieve` 是否已经稳定暴露 dense 与 hybrid 的差异
2. `qa/ask` 是否已经和 `qa/retrieve` 共享同一 retrieval mode
3. 当前返回结构是否足以支撑 Day 25 和 Day 26 的评测
4. 如果要回放一次问答，能否知道它当时跑的是哪种检索模式

## Day 24 的输出物

今天完成后，至少应该留下：

1. 收口后的 `qa/retrieve` 主链路
2. 收口后的 `qa/ask` 主链路
3. 一版明确的 history 兼容策略
4. 一组可以进入 Day 25 评测的最小验证路径

## 今日结论

Day 24 的任务不是继续证明 hybrid retrieval 理论上可行，而是把它变成仓库里真正可调用、可对比、可继续迭代的接口能力。

如果 Day 24 能把这一步站稳，Day 25 和 Day 26 才能开始做有意义的 dense vs hybrid 对比，而不是继续围绕接口口径反复返工。

## 今日实际完成

今天已经完成：

1. `qa/retrieve` 与 `qa/ask` 继续统一复用同一套 `retrievalMode` 主链路
2. `QaAnswerResponse` 已稳定带出 `retrievalMode / fusionStrategy`
3. `qa/history` 已补入 `retrievalMode / fusionStrategy` 回放能力
4. 新写入的历史记录会把 `retrievalMode / fusionStrategy / retrievalResults` 一起持久化为检索快照
5. 老历史记录仍兼容原来的 `retrievedChunks` 数组 JSON，不需要数据库迁移即可回放
6. `QaRecordServiceTest` 已补齐新快照格式与老数组格式的兼容验证
7. 前端 retrieval / qa / history 页面已适配 `retrievalMode / fusionStrategy` 展示与模式切换
8. 已完成真实前后端联调，验证了后端直连、Vite 代理、`DENSE/HYBRID retrieve`、`HYBRID ask` 与 history 回放

## 当前边界

Day 24 完成后，下面这些内容仍然留给后续日期：

1. 还没有完成真实环境 dense vs hybrid 的对比评测
2. 还没有补完整的 retrieval 子阶段日志与指标收口
3. 还没有决定默认模式是否从 `DENSE` 切到 `HYBRID`
4. 还没有形成真实环境 dense vs hybrid 的正式评测结果记录
