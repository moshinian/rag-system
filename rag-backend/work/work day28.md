# Day 28：统一收口 Week 4 README、状态文档、RFC 与表达口径

## 今日目标

Day 27 已经把 Week 4 最后一块“运行时观测口径”补齐了。

所以 Day 28 不再继续扩算法、扩评测或扩监控，而是只做一件事：

**把 Week 4 已经落地的实现、评测结论、观测口径和后续边界，统一收口成仓库里的正式说明。**

今天要解决的问题是：

1. README、`week4.md`、`current-status.md` 之间对 Week 4 的说法是否已经一致；
2. `docs/rfcs` 里哪些文档还停留在 Day 23 之前的旧口径；
3. 面试或复盘时，是否已经可以用一套稳定说法讲清楚 Week 4 做了什么、为什么这样做、效果如何、为什么默认模式仍保持 `DENSE`。

## 为什么 Day 28 做这个

到 Day 27 为止，Week 4 的代码和验证已经具备：

1. `dense recall + keyword recall + RRF fusion`
2. `qa/retrieve / qa/ask / qa/history` 统一 retrieval 契约
3. 两轮真实 `DENSE vs HYBRID` 对比评测
4. 检索与问答关键阶段日志、最小耗时字段与本地日志落盘

但如果 Day 28 不做统一收口，仓库仍会留下几个问题：

1. README 可能还停留在“Week 4 正在继续推进”的口径；
2. RFC 仍可能只记录 Week 3 基线，没有吸收 Week 4 的真实实现结果；
3. 状态文档、周文档、RFC 和实际代码会再次逐步脱节。

所以 Day 28 的任务不是再做新能力，而是：

**把 Week 4 从“已经做完”升级成“已经可交付、可复盘、可讲清楚”。**

## Day 28 要完成什么

### 1. Week 4 总结文档收口

至少需要补齐：

1. `README.md`
2. `work/week4.md`
3. `work/current-status.md`
4. `work/work day28.md`

这些文档最终要统一表达下面结论：

1. Week 4 已完成第一版 hybrid retrieval、真实评测和最小观测收口；
2. `HYBRID` 的收益已被确认，但主要集中在关键词密集题型；
3. 当前默认模式继续保留 `DENSE`，不是因为 hybrid 无效，而是因为延迟成本和更大样本还没有完成最终验收。

### 2. RFC 收口

至少要检查并按真实现状更新：

1. retrieval cache 相关 RFC
2. QA 契约相关 RFC
3. 评测基线相关 RFC
4. RFC 索引里对当前 Week 4 状态的说明

重点不是新增很多 RFC，而是保证已有 RFC 不再落后于真实代码与结论。

### 3. Week 4 最终表达口径

Day 28 结束时，至少要能稳定回答：

1. 为什么要从 dense-only 升级到 hybrid retrieval；
2. 为什么第一版选择 PostgreSQL keyword recall + RRF，而不是 Elasticsearch 或 rerank；
3. hybrid 的收益主要出现在哪些题型；
4. 为什么默认模式仍然保留 `DENSE`；
5. Week 4 完成后，系统相比 Week 3 多了什么工程价值。

## Day 28 的边界

今天明确不做：

1. 不新增 retrieval 算法
2. 不继续扩评测样本
3. 不切默认模式
4. 不补完整 metrics / tracing 平台

今天只做文档、RFC 和最终结论收口。

## Day 28 的输出物

今天完成后，至少应该留下：

1. 一版正式的 `work day28.md`
2. 一版已完成口径的 `week4.md`
3. 一版与代码现状对齐的 `current-status.md`
4. 一版吸收 Week 4 结果后的 README
5. 一版吸收 Week 4 真实实现结果的相关 RFC 更新

## 今日完成情况

Day 28 最终已经完成下面这些收口动作：

1. 新增 `work/work day28.md`，正式记录 Week 4 最终收口目标、边界和输出物
2. `README.md` 已从“Week 4 继续推进中”改成“Week 4 已完成第一版实现、评测和最小观测收口”
3. `work/week4.md` 已补入 Day 28 最终结论，Week 4 现已可作为完整阶段收口材料使用
4. `work/current-status.md` 已把 Week 4 标记为完成态，并把未完成项收敛到 Week 5 以后的继续路线
5. `RFC-0006` 已补入 `retrievalMode` 缓存隔离与坏缓存自愈后的最新口径
6. `RFC-0007` 已补入 `retrievalMode / fusionStrategy`、检索耗时字段和历史回放的新契约
7. `RFC-0009` 已补入 Day 25 / Day 26 的 `DENSE vs HYBRID` 双轨评测资产与正式结论
8. `docs/rfcs/README.md` 已同步当前 Week 4 状态，避免 RFC 索引继续落后于真实仓库现状

## Day 28 收口结论

Week 4 到 Day 28 为止，已经可以稳定收口为下面这句话：

**第一版 hybrid retrieval 已完成实现、接口收口、真实评测与最小观测收口，并确认在关键词密集题型上存在明确收益；但当前默认模式继续保留 `DENSE`，等待更大样本和延迟成本一起进入下一阶段验收。**

这意味着 Week 4 现在已经不是“正在做”，而是：

1. 一项已经落地的工程能力；
2. 一项已经有真实证据支撑的检索优化；
3. 一组已经可以写进 README、RFC 和面试讲稿的正式项目资产。
