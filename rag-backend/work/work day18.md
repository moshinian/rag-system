# Day 18：配置梳理与参数外置

## 今日目标

今天只做一件事：

**把已经进入主链路的关键参数从代码常量迁移到配置，并保证默认值兼容。**

这一天不做新功能扩展，重点是把前面 Day 15 到 Day 17 已落地的工程能力整理成更容易调整、验证和运维的形态。

## 为什么现在做

到 Day 17 为止，系统已经有了：

1. 异步索引
2. 失败重试与恢复
3. 结构化日志

但仍有一批参数写死在代码里，例如：

1. 切块长度和 overlap
2. 索引线程池大小
3. 问答记录默认值

这些值如果继续写死，会带来几个问题：

1. 调参必须改代码再发版
2. README、配置和实际行为容易脱节
3. Day 19 的切块参数实验不好推进

所以 Day 18 的目标不是“多做功能”，而是**把已有能力从可运行整理成可调参**。

## 今日完成

### 1. 切块参数外置

新增：

1. `RagChunkingProperties`
2. `rag.chunking.strategy`
3. `rag.chunking.max-chunk-chars`
4. `rag.chunking.overlap-chars`
5. `rag.chunking.min-break-search-offset`

影响代码：

1. `FixedWindowChunker` 不再依赖写死常量
2. `DocumentProcessingService` 生成 chunk 元数据时会记录当前切块策略名和 overlap

这意味着后续做 Day 19 对比实验时，可以直接改配置，不需要先改代码。

### 2. 线程池参数外置

新增：

1. `RagExecutorProperties`
2. `rag.executor.core-pool-size`
3. `rag.executor.max-pool-size`
4. `rag.executor.queue-capacity`
5. `rag.executor.await-termination-seconds`
6. `rag.executor.thread-name-prefix`

影响代码：

1. `ExecutorConfig` 已改为从配置创建 `indexingExecutor`
2. 线程池参数现在可按环境调优
3. 仍保留了兜底默认值，避免缺配置导致服务启动失败

### 3. 问答记录默认值外置

新增：

1. `RagQaProperties`
2. `rag.qa.default-created-by`
3. `rag.qa.message-type`
4. `rag.qa.prompt-template`
5. `rag.qa.session-name-max-length`

影响代码：

1. `QaRecordService` 不再写死 `qa-service`
2. `message_type` 不再写死 `QA`
3. `prompt_template` 不再写死 `qa-default-v1`
4. 会话名截断长度不再写死 `80`

## 验证结果

今天已完成的验证：

1. `mvn -q -DskipTests compile` 通过
2. `mvn -q -Dtest=DocumentProcessingServiceTest,QaRecordServiceTest,DocumentIndexingServiceTest,DocumentEmbeddingServiceTest,QaServiceTest,QuestionAnsweringServiceTest test` 通过

新增验证点：

1. `DocumentProcessingServiceTest` 已适配外置切块配置
2. `QaRecordServiceTest` 已验证外置配置会真实影响 `createdBy / messageType / promptTemplate / sessionName`

## 今日结论

Day 18 完成后，Week 3 的工程化能力进入下一阶段：

1. 异步索引已具备
2. 重试与恢复已具备
3. 结构化日志已具备
4. 关键参数已经开始可配置化

这为 Day 19 的切块参数实验创造了前置条件。

## 收口说明

Day 18 的真实价值不在于“多了几个配置类”，而在于：

1. Day 19 已经可以直接基于这些参数做对比实验
2. 后续切块调优不再需要先改代码再发版
3. README、配置和实际行为已经开始收拢到同一口径

## 明日建议

Day 19 应该继续做：

1. 选 2 到 3 组切块参数
2. 对同一批样本文档重复切块
3. 比较 chunk 数、平均长度、过短 chunk、过长 chunk
4. 形成可读的实验记录

## Week 3 节奏提醒

Week 3 还剩：

1. Day 19：切块参数对比实验
2. Day 20：问答评测集
3. Day 21：Week 3 验收与文档收口

其中 Day 21 继续保留为收口日，不提前挤占，不在这一天再新增大功能。
