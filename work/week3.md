# 第 3 周执行清单

## 本周目标

第 3 周只做一件事：

**把当前“能跑通”的 RAG 问答链路补成更像真实服务的工程化版本。**

第 3 周结束时，至少要做到：

1. 文档索引链路支持异步执行
2. 索引任务支持状态追踪
3. 失败场景支持最小重试边界
4. 关键链路补齐基础日志与配置口径
5. 形成一组基础问答评测样例

## 当前阶段结论

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
2. 配置梳理
3. 基础评测集
4. 切块参数对比

## 本周验收标准

第 3 周结束时，至少要达到：

1. 文档支持异步索引
2. 可以查询索引任务状态
3. 可以说明失败重试边界
4. 可以展示至少一组评测样例

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
3. Day 18：配置梳理与参数外置已完成，当前已把切块、线程池、问答记录参数迁移到 `application.yml`
4. Day 19：切块参数对比实验已完成，当前已形成第一版参数对比结论
5. Day 20：问答评测集
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
4. `DocumentProcessingService` 生成的 chunk 元数据已改为读取切块配置，而不是写死常量
5. `QaRecordService` 已改为读取问答记录配置，而不是写死 `qa-service / QA / qa-default-v1 / 80`

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
