# Day 15：异步索引起步

## 当前结论

Day 15 已完成。

今天没有继续扩展问答能力，而是把 Week 2 已经做出的同步能力，向 Week 3 的工程化方向推进了一步：

**新增文档异步索引入口，并把任务追踪能力补了起来。**

## Day 15 为什么先做这个

进入 Week 3 后，最先暴露出来的问题不是“不会问答”，而是：

1. 文档处理和 embedding 都是长链路
2. 现有 `/process` 和 `/embed` 需要调用方自己串联
3. 缺少统一的后台任务视角
4. 缺少排队、执行中、完成、失败这些阶段观察能力

所以 Day 15 先把异步索引和状态追踪补上，这样后面的重试、日志、评测才有稳定承载点。

## Day 15 实际完成了什么

今天实际完成了下面几件事：

1. 新增 `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index`
2. 新增 `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks`
3. 新增 `DocumentIndexingService`
4. `indexing_task` 增加 `task_stage / embedded_chunk_count`
5. 新增 `QUEUED` 状态和任务阶段枚举
6. 后台任务已串起 `process -> embed`
7. 同一文档存在未结束索引任务时会拒绝重复提交
8. `DocumentEmbeddingService` 已支持循环处理多批次 chunk，而不是只跑一批

## Day 15 新增的任务阶段

当前索引任务支持的最小阶段包括：

1. `QUEUED`
2. `DOCUMENT_PROCESSING`
3. `DOCUMENT_EMBEDDING`
4. `COMPLETED`

状态仍保持最小集合：

1. `QUEUED`
2. `RUNNING`
3. `SUCCEEDED`
4. `FAILED`

## Day 15 完成后的接口意义

现在调用方可以有两种使用方式：

1. 继续手动调用 `/process` 和 `/embed`
2. 直接调用 `/index`，由后台任务统一串起

这意味着当前索引链路已经从“多个同步动作”前进到“有最小任务编排能力”的阶段。

## Day 15 的边界

今天还没有做这些事情：

1. 自动失败重试
2. 任务取消
3. 任务级分页查询
4. 更完整的结构化日志
5. 评测集与效果对比

这些内容留到后续几天继续推进更合理。

## Day 15 完成后的项目状态

Day 15 完成后，Week 3 已经开始，当前系统除了已有问答闭环外，还新增了第一版工程化索引编排能力：

```text
文档上传 -> 异步索引提交 -> 后台 process -> 后台 embed -> 任务状态查询 -> 问答检索与回答
```
