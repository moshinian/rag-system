# Day 23：Keyword Retrieval 与融合排序起步

## 今日目标

Day 23 正式进入 Week 4 的第一天代码实现，但范围必须控制住：

**先把 hybrid retrieval 最核心的两块打通，也就是 `keyword retrieval` 和 `RRF fusion`。**

Day 23 不追求把整个 Week 4 一次做完，而是优先完成：

1. 一条可以独立运行的 keyword recall；
2. 一条可以把 dense 与 keyword 结果合并的 fusion 逻辑；
3. 一组不会污染现有 dense-only 行为的配置与缓存改动。

## 为什么 Day 23 先做这两块

Day 22 已经把 Week 4 的路线定死为：

```text
dense recall + keyword recall + RRF fusion
```

在这个路线里，Day 23 最应该先做的不是接口字段，也不是评测结果整理，而是先把 retrieval 本身做出来。

原因很明确：

1. 如果 keyword retrieval 还没落地，后续 `retrievalMode=HYBRID` 只是空开关；
2. 如果 fusion 还没落地，`qa/retrieve` 和 `qa/ask` 就无法共享稳定的最终结果；
3. 如果配置和缓存口径不先补齐，后面很容易出现 dense 与 hybrid 缓存串用的问题。

所以 Day 23 的重点不是“把所有文档都更新完”，而是：

**先做出第一版可运行的 retrieval 内核。**

## Day 23 要解决什么

### 1. Keyword retrieval 落地

今天需要先把 keyword recall 做成一个独立能力，至少满足下面几个条件：

1. 不破坏当前 `pgvector` dense retrieval；
2. 不依赖 Elasticsearch 或新中间件；
3. 能对专有名词、接口名、错误码、字段名、中文短语型关键词形成补充召回；
4. 能返回独立 rank，供 fusion 层使用。

Day 23 的 keyword retrieval 不需要一步做到完整搜索引擎，重点是先建立一个可验证的第一版实现。

### 2. RRF fusion 落地

今天还需要把 dual recall 的结果统一融合起来。

当前阶段优先用 `RRF`，因为：

1. 它不要求 dense score 与 keyword score 强行归一化；
2. 它更适合当前这种“先把双路召回串起来”的阶段；
3. 后续如果要补 rerank，也不需要推翻这一步。

Day 23 需要至少完成：

1. dense 与 keyword 结果去重；
2. 基于各自 rank 计算 `RRF score`；
3. 输出稳定的最终排序；
4. 对最终结果再做 `topK` 截断。

### 3. 配置口径补齐

Day 23 不应该把 retrieval 参数写死在 service 里。

至少需要补齐下面几类配置：

1. `defaultMode`
2. `denseCandidateLimit`
3. `keywordCandidateLimit`
4. `fusionK`
5. `keywordMinTokenLength`

这样做的原因不是为了多调参，而是为了：

1. 保证 Day 24 到 Day 26 做对比时口径可控；
2. 避免实现第一天就把 magic number 散到代码里。

### 4. 缓存键修正

Day 23 还要同步考虑 retrieval cache。

当前 `qaRetrieval` 的 key 还只有：

```text
kbCode + ':' + normalizedQuestion + ':' + topK
```

如果不补 `retrievalMode`，就会直接出现：

1. dense 请求误命中 hybrid 缓存；
2. hybrid 请求误命中 dense 缓存；
3. 评测结果和真实接口返回互相污染。

所以 Day 23 必须至少把：

1. `retrievalMode`

纳入 `qaRetrieval` 的 key 设计。

## 建议实现顺序

今天的代码落地建议按下面顺序推进：

### 1. 先补 retrieval mode 与配置对象

先完成：

1. retrieval mode 枚举或等价模型；
2. `RagRetrievalProperties` 配置扩展；
3. `QuestionAnsweringService` 的参数入口改造准备。

这样后面写 service 和缓存时不会反复返工。

### 2. 再落 keyword retrieval 查询

先把 keyword recall 作为独立能力做出来，建议：

1. 单独封装查询方法；
2. 单独返回候选集合；
3. 先不要把 fusion 逻辑揉进 SQL。

当前阶段要刻意避免：

1. 在 repository 层直接写死 dense + keyword 合并；
2. 在 SQL 里提前展开复杂排序公式。

### 3. 再落 fusion 与去重

拿到两路结果后，再在 service 层做：

1. 去重；
2. rank 合并；
3. `RRF` 计算；
4. 最终排序和截断。

只有这样，后续才能：

1. 单独观察 dense recall 结果；
2. 单独观察 keyword recall 结果；
3. 明确 fusion 之后到底变好了还是变差了。

### 4. 最后处理缓存和最小日志

今天不一定要把 Week 4 所有观测都做完，但至少要保证：

1. retrieval mode 进入缓存 key；
2. retrieval 启动和完成日志里能区分 dense-only 与 hybrid；
3. 如果 keyword recall 失败，能明确看到失败发生在 retrieval 的哪一层。

## Day 23 的代码边界

为了防止范围失控，今天明确不做下面这些事：

1. 不补前端联调；
2. 不整理 Week 4 评测结果；
3. 不做 `/qa/history` 快照结构扩展；
4. 不做完整日志指标体系；
5. 不做 rerank；
6. 不引入 Elasticsearch。

今天只做 retrieval 核心链路的第一段。

## 对现有代码的影响面

Day 23 最可能影响下面这些位置：

1. `QuestionAnsweringService`
2. `RagRetrievalProperties`
3. `QuestionRetrievalRequest`
4. `QaAskRequest`
5. `DocumentChunkRepository` 或等价 repository 查询层
6. `CacheNames.QA_RETRIEVAL` 的 key 使用点

如果拆服务，新增代码也最好围绕：

1. `KeywordRetrievalService`
2. `HybridRetrievalService`

这种边界展开，而不是把所有逻辑继续堆进一个方法里。

## Day 23 的验收口径

Day 23 结束时，至少要能回答清楚下面几个问题：

1. keyword retrieval 是否已经能独立跑起来；
2. dense 与 keyword 是否已经能通过统一 fusion 得到最终排序；
3. `retrievalMode` 是否已经进入 retrieval cache key；
4. 当前实现是否仍然允许保留 dense-only 作为对照组；
5. 如果 hybrid 结果不理想，能否单独定位是 keyword recall 问题还是 fusion 问题。

## Day 23 的输出物

今天完成后，至少应该留下：

1. 第一版 keyword retrieval 实现；
2. 第一版 `RRF fusion` 实现；
3. 一版 retrieval mode 配置；
4. 一版修正后的 retrieval cache key；
5. 一版能继续进入 Day 24 的 retrieval 主链路基础。

## 与 Day 24 的关系

Day 23 和 Day 24 的分工要明确：

1. Day 23 先解决“retrieval 能不能以 hybrid 方式跑起来”；
2. Day 24 再解决“`qa/retrieve` 与 `qa/ask` 怎么把这条链路完整对外暴露出来”。

也就是说，Day 23 更像是：

**先把引擎装上。**

而 Day 24 才是：

**把仪表盘和对外接口接好。**

## 今日结论

Day 23 的价值不在于做完所有 Week 4 工作，而在于把最关键的技术风险先打穿：

1. PostgreSQL 方案能不能承接第一版 keyword retrieval；
2. `RRF` 能不能把双路结果稳定融合；
3. 当前代码结构能不能在不推翻现有问答契约的前提下容纳 hybrid retrieval。

如果这三件事今天能站稳，Week 4 后面的接口扩展、评测对比和观测补强才有继续推进的基础。

## 今日实际完成

今天已经完成：

1. `RetrievalMode` 已落地，当前支持 `DENSE / HYBRID`
2. `QuestionAnsweringService.retrieve()` 已支持 dense-only 与 hybrid 双模式
3. 第一版 keyword retrieval 已基于 PostgreSQL 当前数据落地
4. 第一版 `RRF fusion` 已落地，并作为 hybrid 的统一排序策略
5. `QuestionRetrievalRequest / QaAskRequest` 已支持可选 `retrievalMode`
6. `QuestionRetrievalResponse / QaAnswerResponse` 已补入 `retrievalMode / fusionStrategy`
7. `rag.retrieval` 已补入 `defaultMode / denseCandidateLimit / keywordCandidateLimit / fusionK / keywordMinTokenLength`
8. retrieval cache key 已纳入 `retrievalMode`
9. `QuestionAnsweringServiceTest / QaServiceTest / QaRecordServiceTest / RedisCacheConfigTest` 已通过定向验证

## 当前边界

Day 23 完成后，下面这些内容仍然保留到 Day 24 以后：

1. 还没有做真实环境 dense vs hybrid 对比评测
2. 还没有补 `/qa/history` 的 retrieval mode 回放口径
3. 还没有补完整的 retrieval 子阶段日志与指标收口
4. 还没有确认是否要把默认模式从 `DENSE` 切到 `HYBRID`
