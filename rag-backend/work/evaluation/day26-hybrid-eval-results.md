# Day 26 Dense vs Hybrid 真实评测结果

知识库：`day20-cn-kb`  
语言：`zh-CN`  
默认 `topK`：`3`  
对比模式：`DENSE` vs `HYBRID`

## 当前状态

1. Day 25 双轨评测问题集已准备完成
2. Day 25 结果模板与 runbook 已固定
3. 今天已在真实后端服务上完成正式对比结果回填

## 评测结果

| caseCode | category | comparisonFocus | denseRetrievalHit | hybridRetrievalHit | denseAnswerAcceptable | hybridAnswerAcceptable | denseSourceStable | hybridSourceStable | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DAY25-FACT-001 | FACT | FIELD_NAME | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式返回同一组核心 chunk，答案与来源一致 |
| DAY25-FACT-002 | FACT | FIELD_NAME | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式都稳定命中 `对账常见问题#1` |
| DAY25-SUMMARY-001 | SUMMARY | PROCESS_KEYWORD | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式都把 `值班巡检清单#0` 放在首位，没有观测到 hybrid 噪声 |
| DAY25-PROCESS-001 | PROCESS | PROCESS_KEYWORD | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式都稳定命中三阶段恢复步骤 |
| DAY25-PROCESS-002 | PROCESS | PROCESS_KEYWORD | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式结果一致，暂停自动补偿条件回答准确 |
| DAY25-TERM-001 | PROCESS | ASCII_TERM | 是 | 是 | 是 | 是 | 是 | 是 | `HYBRID` 把目标文档 `结算异常处理指南#3` 提到了第 1 位，`DENSE` 仍命中但排在第 3 位 |
| DAY25-FIELD-003 | FACT | FIELD_NAME | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式都准确返回升级说明四项字段 |
| DAY25-FIELD-004 | FACT | FIELD_NAME | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式都稳定命中回放批次号与操作人 |
| DAY25-KEYWORD-005 | FACT | PROCESS_KEYWORD | 是 | 是 | 是 | 是 | 是 | 是 | 两种模式都把 `值班巡检清单#0` 放在首位 |
| DAY25-NOANSWER-001 | NO_ANSWER | NO_ANSWER | 否 | 否 | 是 | 是 | 否 | 否 | 两种模式都正确拒答，但 `sources` 仍带出弱相关 chunk，来源稳定性不能算完全满足 |

## 本轮结论

1. `HYBRID` 相比 `DENSE` 的 retrieval 净收益暂时不成立：`9/9` 可回答问题两种模式都命中预期文档，没有新增命中 case。
2. 收益最明显的是 `ASCII_TERM` 题型：`DAY25-TERM-001` 中，`HYBRID` 把目标文档从第 `3` 位提到第 `1` 位，说明 keyword recall 对混合中英术语排序有帮助。
3. `answer/source` 没有形成二进制指标上的同步改善：两种模式都达到 `10/10` answer acceptable，`9/10` source stable 的问题仍然是无答案题返回了弱相关来源。
4. 本轮没有看到 `HYBRID` 引入新的误召回或明显来源漂移，但也没有在当前样本上拉开可量化优势。
5. 默认模式暂不从 `DENSE` 切到 `HYBRID`：当前收益还停留在局部排序改善，尚不足以推动前后端默认口径修改。

## Day 26 补充样本

为避免第一轮样本过于语义化，Day 26 在同一知识库上追加了一组更偏 `keyword-heavy` 的补充问题，专门放大：

1. 短字段名
2. document lookup
3. 混合中英术语
4. 低语义、强关键词问法

| caseCode | category | comparisonFocus | denseRetrievalHit | hybridRetrievalHit | denseAnswerAcceptable | hybridAnswerAcceptable | denseSourceStable | hybridSourceStable | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DAY26-SUP-001 | PROCESS | PROCESS_KEYWORD | 是 | 是 | 否 | 是 | 否 | 是 | `DENSE` 虽然把 `值班巡检清单#0` 放进 top3，但排在第 3 位，最终直接拒答；`HYBRID` 把目标 chunk 提到第 1 位并回答正确 |
| DAY26-SUP-002 | PROCESS | ASCII_TERM | 否 | 是 | 否 | 是 | 否 | 是 | `DENSE` top3 完全落在 `对账常见问题`，没有召回目标文档；`HYBRID` 直接把 `结算异常处理指南#3` 提到第 1 位 |
| DAY26-SUP-003 | FACT | ASCII_TERM | 否 | 是 | 否 | 是 | 否 | 是 | `spot check` document lookup 上，`DENSE` 直接拒答，`HYBRID` 正确识别为 `结算异常处理指南` |
| DAY26-SUP-004 | FACT | PROCESS_KEYWORD | 否 | 是 | 否 | 是 | 否 | 是 | `失败重试队列长度` document lookup 上，`DENSE` top3 全部偏到 `结算异常处理指南`，`HYBRID` 成功召回 `值班巡检清单#0` |

## 补充样本结论

1. 在这 4 条更偏关键词检索的问题上，`HYBRID` 的 retrieval hit 为 `4/4`，`DENSE` 只有 `1/4`。
2. 在这 4 条问题上，`HYBRID` 的 answer acceptable 为 `4/4`，`DENSE` 为 `0/4`。
3. 这说明第一轮 Day 26 评测之所以没有拉开差距，主要是因为原样本偏语义化、对 exact term 的压力不够。
4. 真实收益已经出现，但它更集中在“短关键词 / document lookup / ASCII term / 低语义问法”这类问题上，而不是所有问题都普遍提升。
5. 即使如此，当前仍暂不切默认模式：因为基线样本的整体收益还不够大，且还没有把延迟成本一起纳入 Week 4 验收。
