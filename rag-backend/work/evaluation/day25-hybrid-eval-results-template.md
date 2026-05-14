# Day 25 Dense vs Hybrid 评测结果模板

知识库：`day20-cn-kb`  
语言：`zh-CN`  
默认 `topK`：`3`  
对比模式：`DENSE` vs `HYBRID`

## 执行顺序

1. 先跑 `retrievalMode=DENSE`
2. 再跑 `retrievalMode=HYBRID`
3. 先记录 retrieval hit
4. 再记录 answer acceptable 与 source stable
5. 最后写 `notes`，说明差异来自 retrieval 还是 answer

## 评测结果

| caseCode | category | comparisonFocus | question | expectedDocument | denseRetrievalHit | hybridRetrievalHit | denseAnswerAcceptable | hybridAnswerAcceptable | denseSourceStable | hybridSourceStable | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DAY25-FACT-001 | FACT | FIELD_NAME | 结算请求积压时，第一步不应该直接做什么？ | 结算异常处理指南 |  |  |  |  |  |  |  |
| DAY25-FACT-002 | FACT | FIELD_NAME | 人工复核差异时至少要记录哪五项信息？ | 对账常见问题 |  |  |  |  |  |  |  |
| DAY25-SUMMARY-001 | SUMMARY | PROCESS_KEYWORD | 值班巡检清单主要关注哪些指标和记录项？ | 值班巡检清单 |  |  |  |  |  |  |  |
| DAY25-PROCESS-001 | PROCESS | PROCESS_KEYWORD | 结算积压恢复动作建议分哪几个阶段执行？ | 结算异常处理指南 |  |  |  |  |  |  |  |
| DAY25-PROCESS-002 | PROCESS | PROCESS_KEYWORD | 什么情况下必须暂停自动补偿？ | 对账常见问题 |  |  |  |  |  |  |  |
| DAY25-TERM-001 | PROCESS | ASCII_TERM | 扩大回放范围前，哪些检查都要正常，连 spot check 也不能漏掉？ | 结算异常处理指南 |  |  |  |  |  |  |  |
| DAY25-FIELD-003 | FACT | FIELD_NAME | 升级到故障处理群时，升级说明里至少要包含哪四项信息？ | 结算异常处理指南 |  |  |  |  |  |  |  |
| DAY25-FIELD-004 | FACT | FIELD_NAME | 如果已经执行过一次回放，人工复核时还要补充哪两项信息？ | 对账常见问题 |  |  |  |  |  |  |  |
| DAY25-KEYWORD-005 | FACT | PROCESS_KEYWORD | 巡检时要重点检查哪三类队列长度是否明显高于日常基线？ | 值班巡检清单 |  |  |  |  |  |  |  |
| DAY25-NOANSWER-001 | NO_ANSWER | NO_ANSWER | 这个知识库里有没有关于北美税务申报的处理说明？ |  |  |  |  |  |  |  |  |

## 结论

1. `HYBRID` 相比 `DENSE` 的 retrieval 净收益：
2. 收益最明显的题型：
3. 是否伴随明显延迟上升：
4. 是否引入新的误召回或来源漂移：
5. 后续需要继续补的 case 或观测字段：
