# Day 13：问答记录持久化

## 当前结论

Day 13 的核心目标是：

**把 Day 11 / Day 12 已经跑通的问答结果持久化下来，并提供最小历史查询能力。**

经过 Day 12，项目已经具备：

1. query embedding
2. TopK 检索
3. Prompt 组装
4. OpenAI-compatible chat completion
5. 第一版 `/qa/ask`
6. 第一版 `sources` 结构化来源返回

所以 Day 13 的工作顺序非常明确：

**提问 -> 回答 -> 来源 -> 持久化 -> 历史查询**

## Day 13 要完成什么

今天做最小可用的问答记录闭环：

1. 创建 `chat_session / chat_message` 表
2. 在 `/qa/ask` 成功后保存问答记录
3. 保存问题、答案、召回结果、来源、模型、耗时
4. 提供历史查询接口

## Day 13 第一版接口目标

建议至少补一个最小历史接口：

`GET /api/knowledge-bases/{kbCode}/qa/history`

支持：

1. `pageNo`
2. `pageSize`

返回至少包含：

1. `sessionCode`
2. `messageCode`
3. `question`
4. `answer`
5. `chatModel`
6. `retrievalResults`
7. `sources`
8. `createdAt`

## Day 13 的实现顺序

建议按下面顺序推进：

1. 先补 Flyway 迁移
2. 新增 `chat_session / chat_message` 实体、mapper、repository
3. 在 `QaService` 里接入记录持久化
4. 新增历史查询 service 和接口
5. 补单测和最小联调

## Day 13 验收标准

今天结束时，至少要达到：

1. `/qa/ask` 成功后可写入问答记录
2. 历史接口可以查到刚保存的问题和答案
3. 来源和召回结果能随记录一起返回
4. 你能讲清楚 Day 13 的数据模型和查询路径

## Day 13 完成后的项目状态

如果今天顺利完成，项目主链路会推进到：

```text
知识库创建 -> 文档上传 -> 解析 -> 切块 -> chunk 入库 -> chunk 向量写库 -> query embedding -> TopK 检索 -> Prompt 组装 -> LLM 回答 -> 来源返回 -> 问答记录持久化
```

这意味着 Day 14 就可以进入：

**端到端联调验收与效果检查。**
