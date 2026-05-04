# 第 3 周执行清单

## 当前结论

Week 3 已完成第一版收口。

这一周没有继续扩问答主链，而是把已经能跑通的系统补成更像真实服务的工程化版本：

1. 异步索引已完成
2. 任务状态追踪已完成
3. 失败重试与卡住任务恢复已完成
4. Redis 业务缓存已完成第一版接入
5. 结构化日志已完成第一版接入
6. 配置梳理与参数外置已完成
7. 切块参数实验已完成
8. 中文问答评测资产与首轮真实评测已完成

## 本周目标

第 3 周只做一件事：

**把当前“能跑通”的 RAG 问答链路补成更像真实服务的工程化版本。**

第 3 周结束时，至少要做到：

1. 文档索引链路支持异步执行
2. 索引任务支持状态追踪
3. 失败场景支持最小重试边界
4. 关键链路补齐基础日志与配置口径
5. 形成一组基础问答评测样例

## 本周起点

Week 2 已完成第一版收口，当前已经具备：

1. 文档上传、解析、切块、chunk 入库
2. chunk embedding 写库
3. TopK 检索
4. Prompt 组装与问答
5. `sources` 来源返回
6. 问答记录与历史查询

进入第 3 周后，重点不再是“把问答做出来”，而是：

**把索引、任务、日志、评测这些工程能力补起来。**

## 本周必须完成

### 1. 异步索引

这部分要完成：

1. 把 `process + embed` 串成后台任务
2. 避免长链路阻塞请求线程
3. 给调用方返回任务受理结果

### 2. 任务状态追踪

这部分要完成：

1. 支持查看任务状态
2. 能区分排队、处理中、向量化中、完成、失败
3. 返回 chunk 数和 embedding 进度等最小信息

### 3. 失败与重试

这部分要完成：

1. 明确失败任务的最小重提交流程
2. 避免同一文档重复并发索引
3. 为后续自动重试预留状态和字段

### 4. 工程化补充

这部分本周继续推进：

1. 结构化日志
2. 基础缓存
3. 配置梳理
4. 基础评测集
5. 切块参数对比

## 本周验收标准

第 3 周结束时，至少要达到：

1. 文档支持异步索引
2. 可以查询索引任务状态
3. 可以说明失败重试边界
4. 可以展示至少一组评测样例

## 本周验收结果

Week 3 当前已经达到验收标准：

1. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index` 已支持异步索引
2. `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks` 已支持任务状态查询
3. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks/{taskId}/retry` 已支持失败任务手动重试
4. 卡住的 `QUEUED / RUNNING` 任务已支持定时恢复扫描
5. 已具备 Redis 业务缓存、结构化日志、配置外置和切块参数实验结果
6. `day20-cn-kb` 已完成中文真实问答评测，可作为 Week 3 验收样例

## Day 15：异步索引起步

今天要完成：

1. 新增文档异步索引入口
2. 新增索引任务查询接口
3. 补齐 `indexing_task` 阶段字段
4. 把 `process + embed` 串成后台任务
5. 避免同文档重复提交中的并发索引

当前结果：

1. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index` 已落地
2. `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks` 已落地
3. `indexing_task` 已新增 `task_stage / embedded_chunk_count`
4. `DOCUMENT_INDEXING` 后台任务已串起 `process + embed`
5. 单文档存在 `QUEUED / RUNNING` 的索引任务时会拒绝重复提交
6. `DocumentEmbeddingService` 已支持按批次循环处理直到当前文档全部 chunk 完成 embedding

## 后续 Day 16 到 Day 21 方向

接下来建议继续按下面顺序推进：

1. Day 16：失败重试机制已完成，当前已支持手动 retry 与卡住任务恢复
2. Day 17：结构化日志已完成，当前已支持 requestId + 异步任务上下文日志
3. Day 18：配置梳理、参数外置与 Redis 业务缓存已完成
4. Day 19：切块参数对比实验已完成，当前已形成第一版参数对比结论
5. Day 20：问答评测集已完成第一版中文评测资产与执行夹具
6. Day 21：Week 3 验收与文档收口
   这一天预留给 README、current-status、week3 文档、验收样例和 Week 3 总结统一收口，不再大幅扩功能

## Day 18：配置梳理与参数外置

今天完成：

1. 把切块参数从代码常量迁移到配置
2. 把异步索引线程池参数迁移到配置
3. 把问答记录的默认值迁移到配置
4. 保持默认值兼容，避免因为缺配置导致启动失败
5. 用单测验证配置已真实影响业务行为

当前结果：

1. 新增 `rag.executor.*`，当前控制索引线程池大小、队列容量、线程名前缀和关闭等待时间
2. 新增 `rag.chunking.*`，当前控制 `FixedWindowChunker` 的策略名、chunk 长度、overlap 和自然断点搜索范围
3. 新增 `rag.qa.*`，当前控制问答记录的 `createdBy / messageType / promptTemplate / sessionNameMaxLength`
4. 新增 `rag.cache.*`，当前控制知识库、文档、chunk、`qa/readiness` 和检索结果缓存 TTL
5. `DocumentProcessingService` 生成的 chunk 元数据已改为读取切块配置，而不是写死常量
6. `QaRecordService` 已改为读取问答记录配置，而不是写死 `qa-service / QA / qa-default-v1 / 80`

## Day 18 补充：基础缓存

今天补齐：

1. Spring Cache + Redis 缓存管理器
2. 知识库读接口缓存
3. 文档详情、列表、chunk 列表缓存
4. `qa/readiness` 就绪度缓存
5. 检索结果短 TTL 缓存
6. 处理、向量化、禁用等写路径缓存失效

当前结果：

1. Redis 已不再只是健康探针，而是已承接业务缓存
2. 当前检索结果缓存采用短 TTL + 写路径整缓存失效策略
3. 当前版本优先保证一致性和简单性，后续仍可继续细化到知识库级别失效

## Day 19：切块参数对比实验

今天完成：

1. 新增可重复执行的切块参数实验测试
2. 增加一份更长的 Markdown 样本，避免 Day 4 样本过短导致实验没有区分度
3. 对 `compact / balanced / wide` 三组参数跑同一批样本文档
4. 比较 chunk 数、平均长度、最短/最长 chunk 和过长 chunk 分布
5. 输出第一版参数结论，供 Day 20 问答评测继续使用

当前结果：

1. `compact(480/60/180)` 总 chunk 数最多，为 `15`
2. `balanced(600/80/240)` 总 chunk 数为 `14`，比 `compact` 少 1 个
3. `wide(720/120/300)` 总 chunk 数降到 `10`，平均 chunk 长度显著上升
4. 在长 Markdown 样本上，`balanced` 已出现 `4` 个 `>500` 字符 chunk，`wide` 出现 `5` 个
5. 当前样本规模下，`balanced` 仍是更适合作为默认值的折中方案

## Day 20：问答评测集

今天完成：

1. 新增三份中文评测样本文档
2. 新增中文问答评测问题集与结果模板
3. 固定 `day20-cn-kb / zh-CN / topK=3` 作为第一版评测口径
4. 新增 `QaEvaluationDatasetTest` 校验评测数据完整性
5. 新增 `QaRetrievalEvaluationIntegrationTest` 作为真实中文检索评测夹具

当前结果：

1. 从 Day 20 开始，后续样本文档和问答问题统一采用中文文本
2. 评测问题已覆盖 `FACT / SUMMARY / PROCESS / NO_ANSWER`
3. `day20-cn-kb` 已完成三份中文文档的真实上传、异步索引和问答评测
4. 5 条可回答问题全部命中预期中文文档，1 条无答案问题成功返回兜底话术
5. Day 21 继续保留为收口日，不提前挤占

## Day 21：验收与收口

今天完成：

1. README 已改成只描述当前真实状态和后续方向
2. `current-status.md` 已同步到 Week 3 完成口径
3. `week3.md` 已补齐验收结果
4. `work day21.md` 已补齐收口说明
5. Redis 业务缓存结果已纳入 Week 3 收口说明

## Week 3 最终结论

Week 3 完成后，当前系统已经从“问答链路能跑”推进到“第一版工程化可交付”：

```text
文档上传 -> 异步索引 -> 失败重试 / 恢复 -> Redis 读缓存 -> 向量检索 -> 问答 -> 记录沉淀 -> 基础评测
```

这一周真正补上的不是新花样，而是后续继续扩展前必须先稳定下来的工程底座。

## Week 4 起点

Week 3 收口后，下一步不再优先补新的主链路能力，而是进入面试化包装阶段：

1. 继续精修 README、架构图和接口说明
2. 整理项目难点、优化点和工程取舍
3. 基于现有中文评测与异步索引能力，沉淀更适合展示的验收样例
4. 准备项目讲稿和深挖问答
