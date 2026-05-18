# Day 16：失败重试与任务恢复

## 当前结论

Day 16 已完成。

今天没有引入新的外部调度系统，而是在 Day 15 的线程池异步模型上继续补齐：

**失败任务可手动重试，卡住任务可被定时扫描恢复。**

## Day 16 为什么做这个

Day 15 的异步索引已经支持：

1. 异步提交索引任务
2. 后台执行 `process -> embed`
3. 查询任务状态

但当时还存在三个明显边界：

1. 服务执行中途退出后，任务可能停留在 `QUEUED / RUNNING`
2. 失败任务没有明确 retry 入口
3. 缺少避免无限恢复的边界控制

所以 Day 16 的目标不是换架构，而是把当前单服务版本补到“最小可恢复”。

## Day 16 实际完成了什么

今天实际完成了下面几件事：

1. 新增 `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks/{taskId}/retry`
2. 新增 `IndexingTaskTriggerSource`
3. `indexing_task` 新增 `parent_task_id / trigger_source / retry_count / max_retry_count / last_heartbeat_at / recovered_at`
4. `DocumentIndexingService` 新增定时恢复扫描
5. 卡住的 `QUEUED / RUNNING` 任务会在超时后自动补投递
6. 恢复和手动重试都会生成新的子任务，并保留原任务记录
7. 已增加最大重试次数控制，避免无限重复恢复

## Day 16 的任务恢复方式

当前恢复机制分成两类：

1. 手动恢复
   - 调用 retry 接口
   - 仅允许对 `FAILED` 任务发起
2. 自动恢复
   - 由定时扫描入口触发
   - 针对长时间无心跳的 `QUEUED / RUNNING` 任务

当前没有引入 Quartz、消息队列或分布式作业系统，恢复仍然基于当前服务实例内的调度和线程池。

## Day 16 完成后的边界

今天已经补齐了最小恢复能力，但还没有做这些事情：

1. 多实例下的分布式任务抢占
2. 更细粒度的任务取消
3. 更完整的结构化恢复日志
4. 基于 Redis / MQ 的独立任务队列

这些内容仍然适合留到后续继续推进。

## Day 16 完成后的项目状态

Day 16 完成后，当前索引链路已经从“异步可提交”继续推进到“最小可恢复”：

```text
文档上传 -> 异步索引提交 -> process -> embed -> 任务状态查询 -> 失败任务重试 -> 卡住任务自动恢复
```
