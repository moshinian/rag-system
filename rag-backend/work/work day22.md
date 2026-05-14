# Day 22：Week 4 混合检索方案定稿

## 今日目标

Day 22 仍然不进入代码实现，但目标已经从“方向说明”收紧为：

**把 Week 4 后续实现需要遵守的技术方案、接口影响面、评测口径和观测字段一次性定清楚。**

当前仓库里已经有：

1. `QuestionAnsweringService.retrieve()` 负责 dense retrieval；
2. `QaService.ask()` 复用 retrieval 结果做 Prompt 组装、LLM 调用和历史持久化；
3. `QuestionRetrievalRequest / QaAskRequest` 当前只接收 `question + topK`；
4. `QuestionRetrievalResponse / QaAnswerResponse` 当前还没有暴露检索模式或双路召回信息；
5. `RFC-0007`、`RFC-0009` 已经把问答契约和评测基线固定下来。

所以 Day 22 的任务不是重新设计整套问答系统，而是：

**在不推翻现有契约的前提下，为 hybrid retrieval 给出一版最小可实现、可对比、可回滚的落地方案。**

## 当前问题

Week 3 收口后，系统已经能做最小可用 RAG 闭环，但 pure dense retrieval 有三个现实问题：

1. 对接口名、错误码、字段名、专有名词这类“关键词必须精确命中”的问题不够稳定；
2. 当前评测基线更擅长回答“系统能不能答”，还不够擅长回答“dense 和 hybrid 到底谁更好”；
3. 当前结构化日志可以看总耗时，但还无法拆清 dense、keyword、fusion、LLM 各阶段的贡献。

这意味着 Week 4 的重点不该再是补更多外围功能，而应该先把 retrieval 本身做扎实。

## Day 22 最终结论

Day 22 最终定下 4 个结论：

1. Week 4 第一版 hybrid retrieval 采用“`dense recall + keyword recall + RRF fusion`”路线；
2. keyword retrieval 第一版不引入 Elasticsearch，不依赖新中间件，直接基于 PostgreSQL 现有数据落地；
3. `/qa/retrieve` 与 `/qa/ask` 共用同一套 retrieval pipeline，不允许两套召回逻辑并行演化；
4. 对外契约先做“兼容式扩展”，新增可选模式字段和最小必要观测字段，不破坏现有 answer、sources、history 语义。

## 技术方案

### 1. 总体链路

Week 4 的 retrieval 链路统一收敛成下面这条：

```text
question
  -> normalize
  -> dense recall
  -> keyword recall
  -> dedupe
  -> RRF fusion
  -> topK truncate
  -> prompt assembly
  -> LLM answer
```

其中：

1. `dense recall` 继续沿用当前 `pgvector` 检索；
2. `keyword recall` 新增一条关键词命中召回；
3. `RRF fusion` 负责把两路结果融合成统一排序；
4. `qa/ask` 不再自己决定召回策略，只消费统一 retrieval 结果。

### 2. 为什么第一版选择 RRF

第一版不做复杂加权学习排序，优先选择 `RRF`，原因很直接：

1. `RRF` 对不同分数体系更友好，不要求 dense score 与 keyword score 先做严格归一化；
2. 实现简单，便于解释，也更适合仓库当前阶段；
3. 如果后续接入 rerank，RRF 可以继续保留为 first-stage fusion，而不是一次性推翻。

当前阶段建议公式保持简单：

```text
rrfScore = 1 / (k + denseRank) + 1 / (k + keywordRank)
```

其中 `k` 先固定为常量，不在 Day 23 就扩成复杂调参系统。

### 3. 为什么 keyword retrieval 不先上 Elasticsearch

当前不引入 Elasticsearch 的原因不是它没价值，而是它不适合本周目标：

1. Week 4 要先验证 hybrid 是否真的带来收益，而不是先引入新的运维复杂度；
2. 当前项目已经用 PostgreSQL 承载主数据和向量数据，第一版 keyword retrieval 继续复用它，工程闭环更短；
3. 当前样本规模、知识库规模和验证目标都允许先做轻量方案。

### 4. 第一版 keyword retrieval 具体边界

第一版 keyword retrieval 不追求“完整搜索引擎”，只解决当前 dense retrieval 最薄弱的问题：

1. 专有名词；
2. 接口名；
3. 错误码；
4. 配置项、字段名；
5. 中文短语型流程关键词。

因此第一版建议采用：

1. 基于 query 归一化后的 term 提取；
2. 对 `document_chunk.content` 做轻量关键词匹配；
3. 返回独立 keyword rank，而不是伪装成 dense score。

推荐的 term 提取口径：

1. 保留原问题全文，作为短语命中候选；
2. 提取英文、数字、下划线、连字符连续串，例如接口名、错误码、字段名；
3. 对中文问题保留长度较长的连续关键词短语，避免过度切碎；
4. 去掉过短、无信息量 token。

推荐的实现顺序：

1. Day 23 第一版先用 repository 查询 + 基础匹配打通；
2. 不在第一版引入复杂分词、倒排表或独立索引表；
3. 如果后续发现 PostgreSQL 原生匹配不足，再考虑更重的索引演进。

## 代码落点

Week 4 的实现最好按当前代码结构落到下面这些位置：

### 1. `QuestionAnsweringService`

当前 `QuestionAnsweringService.retrieve()` 只负责 dense retrieval。Week 4 后应升级为统一 retrieval 编排入口，至少承担：

1. 参数校验与 question normalize；
2. readiness gate；
3. dense recall 调用；
4. keyword recall 调用；
5. fusion、去重和 topK 截断；
6. 返回统一 `QuestionRetrievalResponse`。

如果实现时复杂度过高，建议拆出：

1. `DenseRetrievalService`
2. `KeywordRetrievalService`
3. `HybridRetrievalService`

但对 controller 暴露的主入口仍然保持 `QuestionAnsweringService.retrieve()` 不变，避免接口层提前扩散。

### 2. `QaService`

`QaService.ask()` 当前已经复用 retrieval 结果，这个方向是对的。Week 4 只需要保证：

1. `qa/ask` 与 `qa/retrieve` 使用同一 retrieval mode；
2. `QaAnswerResponse.retrievalResults` 始终来自统一 fusion 后结果；
3. `sources` 继续从最终 retrieval results 映射，不额外引入另一套来源口径。

### 3. Repository 层

Repository 层需要新增 keyword recall 查询，但要遵守两个原则：

1. 不破坏当前 dense query；
2. 不把 fusion 逻辑塞进 SQL。

也就是说：

1. dense recall 继续由现有 `findTopKSimilarChunks()` 一类方法承担；
2. keyword recall 单独新增查询；
3. 双路合并、去重和 RRF 排序放在 service 层完成。

## 接口影响面

### 1. 请求对象

当前 `QuestionRetrievalRequest` 和 `QaAskRequest` 只有：

1. `question`
2. `topK`

Week 4 建议新增一个可选字段：

1. `retrievalMode`

建议枚举值：

1. `DENSE`
2. `HYBRID`

兼容策略必须明确：

1. 新字段是可选的；
2. 旧请求不传时不能直接报错；
3. 默认行为由配置决定，但 Day 23 到 Day 25 建议默认仍保持 `DENSE`，便于做对照；
4. 等 Day 26 完成真实评测后，再决定是否把默认值切到 `HYBRID`。

### 2. `/qa/retrieve` 返回

当前 `QuestionRetrievalResponse` 只有：

1. `knowledgeBaseCode`
2. `question`
3. `embeddingModel`
4. `topK`
5. `hitCount`
6. `chunks`

Week 4 建议补最小必要字段：

1. `retrievalMode`
2. `fusionStrategy`
3. `denseHitCount`
4. `keywordHitCount`

这样做的原因是：

1. 前端或评测夹具能明确知道当前跑的是 dense 还是 hybrid；
2. 日后排查“为什么这次结果变了”时，不需要只靠日志猜。

当前不建议在第一版直接暴露：

1. 全量 dense 候选明细；
2. 全量 keyword 候选明细；
3. 复杂 explain 对象。

先把主链路和稳定字段站住，再决定是否扩详细 explain。

### 3. `/qa/ask` 返回

当前 `QaAnswerResponse` 已有：

1. `question`
2. `answer`
3. `topK`
4. `chatModel`
5. `retrievalResults`
6. `sources`

Week 4 建议只做兼容式补充：

1. `retrievalMode`
2. `fusionStrategy`

其中：

1. `retrievalResults` 继续表示最终进入 Prompt 的结果；
2. `sources` 继续表示面向前端展示的精简证据；
3. 不在第一版把 dense 与 keyword 的候选全集一起塞进 `QaAnswerResponse`。

### 4. `/qa/history`

`RFC-0007` 已经要求历史页持久化问答快照，因此如果 `QaAnswerResponse` 新增 `retrievalMode / fusionStrategy`，要同步考虑：

1. 历史回放是否需要带出这两个字段；
2. 至少要保证后续回看时能知道那次问答跑的是 dense 还是 hybrid。

Day 24 实现时，如果不想立刻改表，可以先把这两个信息放入消息快照 JSON 中，而不是一开始就做新的独立列。

## 配置影响面

当前 `RagRetrievalProperties` 已有：

1. `vectorStore`
2. `defaultTopK`
3. `maxTopK`
4. `maxContextChars`

Week 4 建议继续扩成 retrieval 级配置，而不是把参数散落在 service 里。建议新增：

1. `defaultMode`
2. `denseCandidateLimit`
3. `keywordCandidateLimit`
4. `fusionK`
5. `keywordMinTokenLength`

当前不建议在第一版暴露太多旋钮，原因是：

1. 还没有足够评测数据支撑复杂调参；
2. 先控制变量，更容易解释 dense vs hybrid 的差异。

## 缓存影响面

`RFC-0006` 已经固定 `qaRetrieval` 采用短 TTL + 广失效策略，所以 Week 4 需要同步更新缓存 key 设计。

当前缓存 key 是：

```text
kbCode + ':' + normalizedQuestion + ':' + topK
```

Week 4 后至少要把 `retrievalMode` 纳入 key，否则会出现：

1. dense 请求误命中 hybrid 结果；
2. hybrid 请求误命中 dense 结果。

所以新的 retrieval cache key 至少要包含：

1. `kbCode`
2. `normalizedQuestion`
3. `topK`
4. `retrievalMode`

如果后续再引入 fusion 参数开关，也要继续纳入 key。

## 评测方案

### 1. 评测目标

Week 4 评测不再只问“系统能不能答”，而是要回答：

1. `HYBRID` 相比 `DENSE` 是否有净收益；
2. 收益主要出现在哪些题型；
3. 代价是延迟增加多少；
4. 是否引入新的误召回或噪声问题。

### 2. 数据集扩展方向

在 `day20` 现有中文问题集基础上，Week 4 应优先补下面几类问题：

1. 专有名词型
2. 接口名/错误码型
3. 配置项/字段型
4. 中文流程关键词型
5. 无答案型

这几类问题是 dense retrieval 当前最可能吃亏、hybrid 最有机会体现价值的地方。

### 3. 结果记录字段

Week 4 的评测结果建议至少记录：

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

如果本周时间有限，可以先保证检索对比完整，再补答案层对比。

### 4. Week 4 最低验收结论

Day 26 跑完后，至少要能给出下面这类结论：

1. hybrid 明显提升了哪些 case；
2. hybrid 没提升甚至退化的是哪些 case；
3. 退化原因更像 keyword 噪声、fusion 排序还是样本本身不够区分。

没有这一步，Week 4 的 hybrid 实现就只是“多了一段代码”，不是“多了一项能力”。

## 可观测性方案

### 1. 结构化日志事件

当前已有：

1. `qa.retrieve.started`
2. `qa.retrieve.completed`
3. `qa.ask.started`
4. `qa.ask.completed`

Week 4 建议补下面几类 retrieval 子事件：

1. `qa.retrieve.dense.completed`
2. `qa.retrieve.keyword.completed`
3. `qa.retrieve.fusion.completed`
4. `qa.ask.llm.completed`

### 2. 每阶段至少记录的字段

建议最少记录：

1. `kbCode`
2. `retrievalMode`
3. `topK`
4. `denseCandidateCount`
5. `keywordCandidateCount`
6. `finalHitCount`
7. `denseDurationMs`
8. `keywordDurationMs`
9. `fusionDurationMs`
10. `llmDurationMs`
11. `totalDurationMs`

发生异常时，还需要能看出失败发生在哪个阶段。

### 3. 当前不做什么

Day 22 已明确：

1. Week 4 不建设完整 metrics 平台；
2. 不引入 tracing 系统；
3. 不要求一开始就做图表化展示。

第一版只要求：

1. 日志字段足够支撑定位；
2. 后续若接 Prometheus/Actuator 指标，不需要推翻字段模型。

## 本周不做什么

为了控制范围，Day 22 也明确排除下面这些内容：

1. 不引入 Elasticsearch；
2. 不引入独立 rerank 模型；
3. 不做多轮对话；
4. 不做完整拒答策略重构；
5. 不做新的大型前端交互改版。

Week 4 的重点始终只有一条：

**先把 hybrid retrieval 做成一个可运行、可对比、可解释的版本。**

## Day 23 到 Day 28 执行顺序

### Day 23

1. 落 keyword retrieval 查询
2. 落 RRF fusion
3. 补 retrieval mode 配置与缓存 key

### Day 24

1. 打通 `/qa/retrieve`
2. 打通 `/qa/ask`
3. 补 response 字段
4. 补 history 快照兼容

### Day 25

1. 扩评测问题集
2. 固定 dense vs hybrid 对比模板
3. 补最小夹具或手工执行步骤

### Day 26

1. 跑真实对比评测
2. 记录结果
3. 收敛默认模式是否切换

### Day 27

1. 补 retrieval 子阶段日志
2. 补异常定位字段
3. 统一观测口径

### Day 28

1. 更新 README
2. 更新 current-status / week4
3. 形成 Week 4 阶段结论与表达口径

## 今日输出

Day 22 最终产出应被理解为一份“Week 4 实施约束”：

1. 技术路线已经选定为 `dense + keyword + RRF`；
2. 接口演进已经明确为兼容式扩展；
3. 评测结果要能回答 dense 与 hybrid 的真实差异；
4. 观测字段要能解释检索链路的收益和代价。

这意味着从 Day 23 开始，Week 4 不再缺“要不要做”的结论，只剩“按这个方案把它做完”。
