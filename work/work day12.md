# Day 12：引用来源

## 当前结论

Day 12 的核心目标是：

**在 Day 11 已经可回答的基础上，把来源信息结构化返回，而不是只返回答案文本。**

经过 Day 11，项目已经具备：

1. query embedding
2. TopK 检索
3. Prompt 组装
4. OpenAI-compatible chat completion
5. 第一版 `/qa/ask` 问答闭环

所以 Day 12 的工作顺序非常明确：

**问题输入 -> 检索 -> 回答 -> 结构化返回来源**

## Day 12 要完成什么

今天只做引用来源结构，不扩展问答记录：

1. 设计引用来源响应结构
2. 从现有 `retrievalResults` 中提取来源字段
3. 统一来源信息表达
4. 让 `/qa/ask` 返回答案的同时返回来源列表

## Day 12 第一版结构目标

建议来源结构至少包含：

1. `documentCode`
2. `documentName`
3. `chunkId`
4. `chunkIndex`
5. `content`
6. `score`
7. `startOffset`
8. `endOffset`

## Day 12 的实现顺序

建议按下面顺序推进：

1. 抽出 citation/source response 模型
2. 从 Day 11 的 `retrievalResults` 映射出来源结果
3. 让 `/qa/ask` 的返回结构补充 `sources`
4. 补 service 单测和接口联调

## Day 12 验收标准

今天结束时，至少要达到：

1. `/qa/ask` 返回最终答案
2. `/qa/ask` 同时返回结构化来源
3. 来源结果可直接映射到文档和 chunk
4. 你能讲清楚答案与来源之间的关系

## Day 12 完成后的项目状态

如果今天顺利完成，项目主链路会推进到：

```text
知识库创建 -> 文档上传 -> 解析 -> 切块 -> chunk 入库 -> chunk 向量写库 -> query embedding -> TopK 检索 -> Prompt 组装 -> LLM 回答 -> 来源返回
```

这意味着 Day 13 就可以直接进入：

**问答记录持久化。**
