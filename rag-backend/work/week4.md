# 第 4 周执行清单

## 当前结论

Week 4 的重点不再是继续补“有没有 RAG 主链路”，因为当前系统已经完成：

1. Week 1 文档入库主链路
2. Week 2 向量检索、问答、来源返回与历史闭环
3. Week 3 异步索引、失败恢复、Redis 业务缓存、结构化日志、配置外置、切块实验与中文评测收口

当前系统已经具备最小可用的企业知识库 RAG 问答闭环，但还存在三个明显短板：

1. 当前检索仍以单阶段 dense retrieval 为主，对专有名词、接口名、错误码、流程关键词类问题的召回稳定性还不够；
2. 当前评测仍停留在第一版中文样本和人工观察层面，还缺少更明确的检索对比口径；
3. 当前可观测性仍以结构化日志和健康检查为主，还缺少更聚焦检索与问答链路的关键指标。

所以 Week 4 不再扩大量新功能，而是聚焦一条主线：

**把当前单阶段 dense retrieval 升级为“可评测、可观测”的第一版混合检索系统。**

## 本周目标

第 4 周只做一件事：

**完成第一版 hybrid retrieval，并同步补齐评测对比和最小可用的检索可观测性。**

第 4 周结束时，至少要做到：

1. 支持 dense retrieval + keyword retrieval 双路召回
2. 支持一版稳定、可解释的融合排序策略
3. `qa/retrieve` 与 `qa/ask` 可共用 hybrid retrieval
4. 输出一版 dense vs hybrid 的评测对比结果
5. 补齐检索与问答关键链路的最小观测信息
6. 能把为什么做、怎么做、做完后效果如何讲清楚

## 本周起点

进入 Week 4 前，仓库已经具备下面这些基础：

1. 文档上传、解析、切块、chunk 入库
2. `pgvector` 向量写库与 query embedding
3. `POST /qa/retrieve` 与 `POST /qa/ask`
4. `sources` 结构化来源返回
5. `qa/readiness` 检索门禁
6. 问答记录与历史查询
7. 异步索引、失败重试与任务恢复
8. Redis 业务缓存
9. 结构化日志与配置外置
10. 切块参数实验与第一版中文问答评测

这意味着 Week 4 不需要从零做检索，而是在已有 dense retrieval 基础上补混合检索、效果评测和运行观测。

## 本周必须完成

### 1. 混合检索主链路

这部分要完成：

1. 在当前 dense retrieval 之外，增加一条 keyword retrieval 能力
2. 让同一问题可以同时经过 dense 与 keyword 两路召回
3. 对双路召回结果做融合、去重和统一排序
4. 保持 `qa/retrieve` 与 `qa/ask` 走同一套召回策略
5. 让接口层可以明确区分当前请求是 dense-only 还是 hybrid

当前阶段的设计取舍要明确：

1. 第一版优先选择轻量、可讲清楚、便于验证的 keyword retrieval 方案
2. 第一版不强制引入 Elasticsearch 或专用向量数据库
3. 第一版融合策略优先选择简单可解释方案，例如加权融合或 RRF
4. 重点不是一次做到最优，而是先建立一版稳定可对比的 hybrid retrieval

第 4 周结束时，至少要达到：

1. pure dense 与 hybrid retrieval 都可以稳定执行
2. 对专有名词、流程关键词、接口名、错误码类问题，hybrid 在至少一部分样例上优于 dense-only
3. 检索结果仍保留 `documentCode / chunkIndex / content / score` 等证据字段

### 1.1 Week 4 固定技术路线

Day 22 之后，本周技术路线不再悬而未决，统一固定为：

```text
dense recall + keyword recall + RRF fusion
```

这里的关键取舍已经明确：

1. dense recall 继续复用当前 `pgvector` 主链路；
2. keyword recall 第一版不引入 Elasticsearch，直接基于 PostgreSQL 当前数据落地；
3. fusion 第一版统一采用 `RRF`，不在本周展开复杂学习排序；
4. rerank 继续留在 `RFC-0003` 的规划范围内，不混入 Week 4 第一版实现。

这样做的原因是：

1. 能最快建立一版真实可运行的 hybrid retrieval；
2. 能避免分数归一化和多路权重调参在本周过度扩散；
3. 能把“检索优化是否有效”与“新基础设施是否值得引入”分开验证。

### 1.2 接口与代码影响面

Week 4 虽然以 retrieval 为主，但必须明确影响当前哪些真实对象：

1. `QuestionAnsweringService.retrieve()` 会从 dense-only 升级成统一 retrieval 入口；
2. `QaService.ask()` 继续复用 retrieval 结果，不允许单独演化另一套召回逻辑；
3. `QuestionRetrievalRequest` 与 `QaAskRequest` 建议新增可选 `retrievalMode`；
4. `QuestionRetrievalResponse` 建议新增 `retrievalMode / fusionStrategy / denseHitCount / keywordHitCount`；
5. `QaAnswerResponse` 建议新增 `retrievalMode / fusionStrategy`，但继续保留现有 `retrievalResults / sources` 语义不变。

这部分的演进原则只有一条：

**兼容式扩展，不推翻现有问答契约。**

### 1.3 配置与缓存影响面

Week 4 不是只改 service 逻辑，还会影响当前 retrieval 配置和缓存键：

1. `rag.retrieval` 建议新增 `defaultMode / denseCandidateLimit / keywordCandidateLimit / fusionK / keywordMinTokenLength`
2. `qaRetrieval` 缓存 key 必须纳入 `retrievalMode`
3. 如果后续再新增 fusion 相关开关，也要继续纳入 retrieval cache key

否则会直接出现：

1. dense 请求误命中 hybrid 缓存；
2. hybrid 请求误命中 dense 缓存；
3. 评测和真实接口返回互相污染。

### 2. 评测体系补强

这部分要完成：

1. 在现有中文评测集基础上，增加更适合检索策略对比的问题类型
2. 区分“检索是否命中”和“回答是否可接受”两个维度
3. 固定 dense-only 与 hybrid 两种策略的对比口径
4. 输出一版 Week 4 的对比结果

建议重点补的题型包括：

1. 专有名词型
2. 流程关键词型
3. 配置项或字段型
4. 无答案型

每条问题至少要记录下面几项：

1. `question`
2. `expectedDocument`
3. `expectedKeywords`
4. `denseHit`
5. `hybridHit`
6. `answerAcceptable`
7. `notes`

第 4 周结束时，至少要达到：

1. 可以输出一份 dense vs hybrid 的评测对比结果
2. 可以明确指出 hybrid 更适合解决哪些问题
3. 可以明确指出当前仍未解决的召回或回答问题

### 2.1 Week 4 评测结果至少要回答什么

Week 4 的评测不是简单追加几条 case，而是要能回答下面 4 个问题：

1. `HYBRID` 相比 `DENSE` 是否有净收益；
2. 收益主要出现在什么题型；
3. 收益是否伴随明显延迟上升；
4. 是否引入了新的误召回或噪声。

如果最终结果无法回答这 4 个问题，本周评测就还不算收口。

### 2.2 建议的 Week 4 结果字段

在 `day20` 现有字段基础上，Week 4 结果模板建议至少补成：

1. `denseRetrievalHit`
2. `hybridRetrievalHit`
3. `denseAnswerAcceptable`
4. `hybridAnswerAcceptable`
5. `denseSourceStable`
6. `hybridSourceStable`
7. `notes`

如果时间不足，可以先保证 retrieval 对比完整，再补 answer/source 两层对比。

### 3. 可观测性第一版补强

这部分要完成：

1. 在现有结构化日志基础上，补齐混合检索相关的关键观测信息
2. 能区分 dense retrieval、keyword retrieval、fusion 和问答各阶段耗时
3. 让后续排查问题时可以知道“慢在哪里、失败在哪里、hybrid 带来了什么变化”

建议优先补齐下面这些信息：

1. 当前检索模式：dense-only / hybrid
2. dense 召回耗时
3. keyword 召回耗时
4. 融合排序耗时
5. 最终召回条数
6. 问答总耗时
7. LLM 调用耗时与失败情况

当前阶段的取舍也要明确：

1. Week 4 不把目标扩成完整监控平台建设
2. 第一版重点是关键日志与指标可统计、可展示、可解释
3. 重点服务检索链路调优，而不是一开始就铺开所有系统指标

第 4 周结束时，至少要达到：

1. 可以通过日志或指标区分 dense 与 hybrid 请求
2. 可以定位检索链路的主要耗时阶段
3. 发生异常时，可以定位是 dense、keyword、fusion 还是 LLM 阶段的问题

### 3.1 Week 4 建议补的结构化日志事件

除了当前已有的：

1. `qa.retrieve.started`
2. `qa.retrieve.completed`
3. `qa.ask.started`
4. `qa.ask.completed`

Week 4 建议再补：

1. `qa.retrieve.dense.completed`
2. `qa.retrieve.keyword.completed`
3. `qa.retrieve.fusion.completed`
4. `qa.ask.llm.completed`

### 3.2 Week 4 最少观测字段

建议最少记录：

1. `retrievalMode`
2. `denseCandidateCount`
3. `keywordCandidateCount`
4. `finalHitCount`
5. `denseDurationMs`
6. `keywordDurationMs`
7. `fusionDurationMs`
8. `llmDurationMs`
9. `totalDurationMs`

Week 4 不要求一口气接成完整监控平台，但至少要保证日志字段足以做排障和评测解释。

## 本周验收标准

第 4 周结束时，至少要能回答清楚下面这些问题：

1. 为什么当前系统需要混合检索，而不是只保留向量召回？
2. 第一版 hybrid retrieval 的设计取舍是什么？
3. dense-only 和 hybrid 的效果差异体现在哪些题型上？
4. 如果 hybrid 没有明显提升，是检索设计问题、评测样本问题，还是融合策略问题？
5. 新检索链路的耗时和失败情况如何观测？

另外还有一个隐含验收问题必须能回答：

6. 为什么 Week 4 先做 `dense + keyword + RRF`，而不是直接引入 Elasticsearch 或 rerank？

## 本周验收结果目标

Week 4 完成后，理想状态下应该形成下面这条新闭环：

```text
问题输入 -> dense retrieval + keyword retrieval -> 融合排序 -> Prompt 组装 -> LLM 回答 -> 来源返回 -> 评测对比 -> 日志/指标观测
```

这意味着系统会从“第一版能问答”继续推进到“第一版更像成熟 RAG 服务”。

## 后续 Day 22 到 Day 28 方向

接下来建议继续按下面顺序推进：

1. Day 22：明确 hybrid retrieval 技术方案与接口影响面
2. Day 23：完成 keyword retrieval 与融合排序第一版实现
3. Day 24：打通 `qa/retrieve` 与 `qa/ask` 的 hybrid retrieval 主链路
4. Day 25：补齐 dense vs hybrid 的评测样本与对比口径
5. Day 26：完成第一轮真实对比评测并记录结果
6. Day 27：补齐检索与问答关键日志/指标
7. Day 28：统一收口 README、状态文档、Week 4 结论与面试表达素材

每一天的输入输出关系也要明确：

1. Day 22 负责把实现边界定死，避免 Day 23 以后边写边改口径；
2. Day 23 和 Day 24 负责把 hybrid 主链路跑通；
3. Day 25 和 Day 26 负责证明这条链路值不值得保留；
4. Day 27 和 Day 28 负责把结果变成可运维、可交付、可表达的工程资产。

## 当前进展

截至 Day 23，Week 4 已经不再只是计划：

1. `RetrievalMode` 已落地，当前支持 `DENSE / HYBRID`
2. `QuestionAnsweringService.retrieve()` 已支持第一版 hybrid retrieval 编排
3. PostgreSQL 内第一版 keyword retrieval 已落地
4. 第一版 `RRF fusion` 已落地
5. retrieval cache key 已纳入 `retrievalMode`
6. `/qa/retrieve` 与 `/qa/ask` 已支持可选 `retrievalMode`
7. `QuestionRetrievalResponse / QaAnswerResponse` 已补入 `retrievalMode / fusionStrategy`

当前仍然没有完成的部分也要明确保留：

1. 还没有做真实环境 dense vs hybrid 对比评测
2. 还没有补 `/qa/history` 的 retrieval mode 回放口径
3. 还没有补完整的 retrieval 子阶段日志与指标收口
4. 还没有完成 README、状态文档之外的全部 Week 4 最终验收材料

## Week 4 结束后的预期收益

如果 Week 4 顺利完成，当前项目会新增三层价值：

1. 在技术深度上，不再只是单阶段向量检索，而是具备第一版混合检索能力；
2. 在 AI 工程化上，不再只是“能演示”，而是具备明确的评测对比与效果判断口径；
3. 在系统交付上，不再只靠人工看结果，而是开始具备面向检索和问答链路的最小可观测性。

对简历和面试来说，Week 4 的价值也很直接：

1. 可以更自然地证明你理解 dense retrieval 的边界；
2. 可以更自然地证明你不是只会调 API，而是会做检索优化、效果验证和系统治理；
3. 可以让你的项目从“AI Demo”进一步接近“企业级 AI 应用工程项目”。
