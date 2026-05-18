# Day 17：结构化日志

## 当前结论

Day 17 已完成。

今天没有继续扩展索引功能，而是把 Week 3 需要的日志与可观测性补到了第一版：

**请求日志、异常日志、异步索引日志、问答日志都已经统一为结构化 key=value 形式。**

## Day 17 为什么做这个

进入 Week 3 后，项目已经不缺“能跑”的接口，真正的工程问题变成了：

1. 出问题时难以快速定位是哪条请求、哪个任务、哪篇文档
2. 异步线程和主请求线程之间的 requestId 上下文容易断掉
3. 缺少统一格式，日志难 grep、难追踪、难讲清楚

所以 Day 17 的目标不是加更多业务能力，而是让现有链路更可观察。

## Day 17 实际完成了什么

今天实际完成了下面几件事：

1. 新增 `StructuredLogMessage`
2. `RequestIdFilter` 已补充请求开始/结束结构化日志
3. `RequestIdFilter` 已接入 `MDC`
4. `ExecutorConfig` 已支持异步线程池透传 `MDC`
5. `GlobalExceptionHandler` 已补充校验异常、业务异常、未预期异常日志
6. `DocumentIndexingService` 已补充提交、重试、恢复、开始、成功、失败日志
7. `DocumentProcessingService` 已补充处理开始/成功/失败日志
8. `DocumentEmbeddingService` 已补充批次级日志
9. `QuestionAnsweringService / QaService` 已补充检索和问答日志
10. `OpenAiCompatibleClient` 已补充 LLM 调用失败日志

## Day 17 完成后的日志特点

当前日志的第一版特点包括：

1. 统一使用 `event=...` 作为事件名
2. 日志字段以 `key=value` 输出
3. 包含 `requestId`、`taskId`、`kbCode`、`documentCode` 等关键上下文
4. 异步索引线程可以继承请求线程的 `MDC`

这意味着现在已经可以更稳定地追踪：

1. 某个请求触发了什么
2. 某个索引任务进入了哪个阶段
3. 某次问答用了什么模型和多少召回结果
4. 某次失败发生在什么节点

## Day 17 的边界

今天还没有做这些事情：

1. JSON 日志输出
2. 日志采集与聚合平台接入
3. 指标监控和 tracing 系统接入
4. 更细粒度的 SQL 审计日志治理

这些内容留到后面继续补更合理。

## Day 17 完成后的项目状态

Day 17 完成后，Week 3 已经从“异步索引可用”继续推进到了“可观察”阶段：

```text
请求接入 -> requestId 透传 -> 异步索引 / 问答执行 -> 结构化日志输出 -> 异常定位
```
