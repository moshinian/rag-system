# Day 25 Dense vs Hybrid 评测执行步骤

## 目标

这份 runbook 只解决一件事：

把 `DENSE` 和 `HYBRID` 的真实评测跑法固定下来，避免 Day 26 一边执行一边改字段。

## 输入资产

1. 问题集：`day25-hybrid-eval-cases.json`
2. 结果模板：`day25-hybrid-eval-results-template.md`
3. 基线样本：`day20-cn-kb` 及其三份中文样本文档

## 固定口径

1. 检索参数固定为 `topK=3`
2. 执行顺序固定为先 `DENSE`、后 `HYBRID`
3. 每条 case 先判断 retrieval，再判断 answer 和 source
4. `notes` 只记录差异和异常，不重复抄答案全文

## 逐题执行步骤

1. 用 `retrievalMode=DENSE` 调 `qa/retrieve`
2. 记录 `denseRetrievalHit`
3. 再用 `retrievalMode=HYBRID` 调 `qa/retrieve`
4. 记录 `hybridRetrievalHit`
5. 如果 retrieval 没命中，不要先把问题归咎到 answer
6. 只有 retrieval 基本命中后，才继续比较 `qa/ask`
7. 分别记录 `denseAnswerAcceptable / hybridAnswerAcceptable`
8. 再根据 `sources` 记录 `denseSourceStable / hybridSourceStable`
9. 最后在 `notes` 里说明收益点、误召回或来源漂移

## 判定规则

### retrievalHit

1. `SHOULD_ANSWER`：只要命中 `expectedDocument`，即可记为命中
2. `SHOULD_REJECT`：如果没有命中明确相关文档，且没有明显强相关证据，可记为未命中

### answerAcceptable

1. 回答覆盖主要事实、步骤或字段，可记为“是”
2. 回答严重遗漏核心条件、阶段或字段，可记为“否”
3. 无答案 case 如果明确拒答、不胡乱编造，可记为“是”

### sourceStable

1. `sources` 与回答主张一致，可记为“是”
2. 来源只弱相关、但回答下了强结论，可记为“否”
3. 介于两者之间时，可在 `notes` 标成“部分满足”

## Day 26 最终需要回答的问题

1. `HYBRID` 相比 `DENSE` 是否有净收益
2. 收益主要集中在哪些 `comparisonFocus`
3. 收益是否伴随明显误召回增加
4. 当前收益更像 retrieval 提升，还是 answer 组织改善
