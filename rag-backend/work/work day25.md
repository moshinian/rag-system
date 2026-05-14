# Day 25：补齐 Dense vs Hybrid 评测样本与对比口径

## 今日目标

Day 23 和 Day 24 已经把 hybrid retrieval 的实现、接口收口、history 回放和前后端联调打通。Day 25 的重点不再是继续改主链路，而是：

**把后续真实对比评测需要的样本、字段和执行口径补齐。**

今天要解决的不是“系统能不能跑”，而是：

1. `DENSE` 和 `HYBRID` 到底应该怎么比；
2. 哪些 case 真正能体现 hybrid 的价值；
3. 评测结果需要记录到什么粒度，后面才不会重新返工。

## 为什么 Day 25 做这个

到 Day 24 为止，仓库已经具备：

1. `RetrievalMode`
2. PostgreSQL 内第一版 keyword retrieval
3. 第一版 `RRF fusion`
4. `qa/retrieve / qa/ask / qa/history` 的统一口径
5. 前端展示与模式切换
6. 一次真实前后端联调

如果没有 Day 25 这一步，后面会出现两个问题：

1. Day 26 即使跑出结果，也很难证明收益来自 retrieval 设计，而不是样本偶然性；
2. `DENSE` 和 `HYBRID` 的对比字段容易一边跑一边改，最后很难复盘。

所以 Day 25 的任务是先把评测框架站稳，而不是先追着分数跑。

## Day 25 要完成什么

### 1. 扩充更适合对比的 case

今天需要优先补下面几类问题：

1. 专有名词型
2. 接口名 / 错误码型
3. 配置项 / 字段型
4. 中文流程关键词型
5. 无答案型

补这些题型的原因很直接：

1. 它们最能暴露 dense retrieval 的边界；
2. 也是第一版 hybrid retrieval 最可能体现价值的地方。

### 2. 固定结果字段

Day 25 需要把结果模板至少固定到下面这些字段：

1. `caseCode`
2. `question`
3. `expectedDocument`
4. `expectedKeywords`
5. `denseRetrievalHit`
6. `hybridRetrievalHit`
7. `denseAnswerAcceptable`
8. `hybridAnswerAcceptable`
9. `denseSourceStable`
10. `hybridSourceStable`
11. `notes`

如果时间还够，最好再预留延迟观察字段，但不强制今天就把自动统计做完。

### 3. 明确执行顺序

Day 26 跑真实评测前，Day 25 需要把执行顺序定清楚：

1. 先跑 `retrievalMode=DENSE`
2. 再跑 `retrievalMode=HYBRID`
3. 先记 retrieval hit
4. 再记 answer acceptable 与 source stable
5. 最后写 notes，总结具体差异

这样做的原因是避免把“检索没命中”和“回答组织不佳”混成一个问题。

### 4. 保持与 RFC-0009 连续

Day 25 不应该重新发明一套完全独立的评测体系，而是要建立在 Day 20 评测基线之上。

也就是说：

1. 继续沿用中文样本和当前知识库体系；
2. 继续沿用 `retrievalHit / answerAcceptable / sourceStable` 这三层思路；
3. 只是把它升级成 `dense vs hybrid` 的双轨对比口径。

## Day 25 的边界

今天明确不做：

1. 不写最终评测结论
2. 不做完整自动评分系统
3. 不补新的 retrieval 算法
4. 不做完整观测平台接入

今天只做评测资产和对比口径准备。

## Day 25 的验收口径

Day 25 结束时，至少要能回答清楚下面几个问题：

1. Day 26 应该跑哪些问题
2. 每个问题应该记录哪些结果字段
3. 如何区分 retrieval 层收益和 answer 层收益
4. 哪些 case 预期最能体现 hybrid 的价值

## Day 25 的输出物

今天完成后，至少应该留下：

1. 扩充后的评测问题集
2. 固定后的 dense vs hybrid 结果模板
3. 一版可执行的评测步骤

## 今日结论

Day 25 的任务不是继续做功能，而是为 Day 26 的真实评测建立一套稳定、可回看、可解释的跑法。

如果 Day 25 能把样本和口径站稳，Day 26 的结果才会真正有说服力。
