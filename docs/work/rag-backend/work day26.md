# Day 26：完成 Dense vs Hybrid 真实对比评测并收口 Week 4

## 今日目标

Day 25 已经把样本、字段和执行口径固定下来。Day 26 的任务不再是继续准备，而是：

**在真实环境里跑完 `DENSE vs HYBRID` 对比，并把 Week 4 的评测结论真正落到仓库里。**

今天要回答的不是“理论上 hybrid 有没有价值”，而是：

1. 在当前中文知识库里，`HYBRID` 相比 `DENSE` 是否真的有净收益；
2. 收益主要体现在哪些题型；
3. 收益是否伴随明显噪声、来源漂移或回答退化；
4. 前后端默认口径是否要跟着调整。

## 为什么 Day 26 做这个

到 Day 25 为止，仓库已经具备：

1. `RetrievalMode`
2. 第一版 PostgreSQL keyword retrieval
3. 第一版 `RRF fusion`
4. `qa/retrieve / qa/ask / qa/history` 的统一检索口径
5. Day 25 双轨评测问题集、模板和 runbook

如果 Day 26 不跑真实对比，Week 4 就还停留在“功能实现完成，但收益没有落证据”的状态。

所以 Day 26 的目标很明确：

**把 Week 4 从“hybrid 能跑”推进到“hybrid 的收益、边界和默认口径都被真实验证”。**

## Day 26 要完成什么

### 1. 跑真实 retrieval 对比

按 Day 25 的固定顺序：

1. 先跑 `retrievalMode=DENSE`
2. 再跑 `retrievalMode=HYBRID`
3. 逐题记录 `denseRetrievalHit / hybridRetrievalHit`

### 2. 跑真实 ask 对比

在 retrieval 结果基础上，再逐题记录：

1. `denseAnswerAcceptable / hybridAnswerAcceptable`
2. `denseSourceStable / hybridSourceStable`
3. 关键差异写入 `notes`

### 3. 收口 Week 4 结论

至少要明确：

1. hybrid 的净收益是否成立
2. 收益更偏 retrieval 层，还是 answer/source 层
3. 当前默认模式是否继续保留 `DENSE`
4. 前端默认初始值是否需要同步修改

## Day 26 的边界

今天明确不做：

1. 不补新的 retrieval 算法
2. 不扩更多评测知识库
3. 不接完整 metrics 平台
4. 不做 rerank

今天只做真实评测、结论收口和必要的一致性调整。

## Day 26 的输出物

今天完成后，至少应该留下：

1. 一份真实 `DENSE vs HYBRID` 对比结果
2. 一份可回看的结论记录
3. 一份关于默认模式是否切换的决定

## 今日实际完成

今天已经完成：

1. 启动真实后端服务，并使用 `DASHSCOPE_API_KEY / DEEPSEEK_API_KEY` 完成 embedding 与 LLM 连通性校验
2. 复用现有 `day20-cn-kb`，确认 `qa/readiness` 为 `READY`，文档状态均为 `INDEXED`
3. 按 Day 25 runbook 在真实服务上跑完 10 条 case 的 `DENSE / HYBRID retrieve + ask`
4. 产出正式结果文件 `work/evaluation/day26-hybrid-eval-results.md`
5. 确认当前 9 条可回答问题上，`DENSE` 与 `HYBRID` 都达到 `9/9 retrieval hit`
6. 确认当前 10 条问题上，`DENSE` 与 `HYBRID` 都达到 `10/10 answer acceptable`
7. 确认当前 10 条问题上，`DENSE` 与 `HYBRID` 都是 `9/10 source stable`，剩余问题仍然是无答案题返回弱相关来源
8. 识别到唯一明确收益是 `DAY25-TERM-001`：`HYBRID` 把目标文档提升到了第 1 位，但还没有转化成更高的命中率或回答收益
9. 结论上继续保留后端 `defaultMode=DENSE` 与前端 retrieval / qa 页面 `initialValues.retrievalMode=\"DENSE\"`，当前前后端默认口径保持一致，不做切换
10. 在同一知识库上继续补了一组更偏关键词检索的 Day 26 补充样本
11. 补充样本中，`HYBRID` 在 `4/4` 问题上都完成了正确召回并回答，`DENSE` 只有 `1/4` retrieval hit、`0/4` answer acceptable
12. 这次补样本后，Week 4 的真实结论从“暂时没有量化收益”修正为“收益存在，但集中在短关键词 / document lookup / ASCII term 题型”

## 今日结论

Day 26 已经把 Week 4 最关键的验证补完了：

1. 第一版 `HYBRID` 在当前中文评测集上可以稳定运行，没有引入明显回退
2. 原始 Day 25 基线样本没有拉开足够差距，但 Day 26 补充样本已经证明 `HYBRID` 在关键词密集问题上存在真实净收益
3. 当前 hybrid 的价值已经不只是排序改善，而是在一批短关键词 / ASCII term / document lookup 问题上直接把拒答变成正确回答
4. 即便如此，Week 4 仍继续保留 `DENSE` 作为默认模式，因为还没有把更大样本和延迟成本一起纳入最终验收
5. 因此 Week 4 可以收口为“hybrid 已完成第一版实现、真实评测和补充样本验证，并确认在关键词密集题型上存在明确收益，但默认模式继续保留 `DENSE`”

## 当前边界

Day 26 完成后，Week 4 仍然留下这些后续空间：

1. 还需要补更多能放大 hybrid 差异的样本，尤其是专有名词、接口名、错误码和更长字段名题型
2. 还需要补延迟统计字段，当前结果还不能回答 `HYBRID` 是否带来可感知耗时上升
3. 还需要继续处理无答案题返回弱相关来源的问题，这仍然是 retrieval/source 契约的剩余缺口
4. 在新的评测集真正出现净收益前，不建议把后端默认模式和前端默认初始值切到 `HYBRID`
