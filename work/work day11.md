# Day 11：问答组装

## 当前结论

Day 11 的核心目标是：

**在 Day 10 检索结果基础上，完成第一版 Prompt 组装，并接通大模型回答。**

经过 Day 10，项目已经具备：

1. query embedding
2. `pgvector` TopK 检索
3. 检索结果返回结构
4. `day6-kb` 真实召回联调

所以 Day 11 的工作顺序非常明确：

**问题输入 -> query embedding -> TopK 检索 -> Prompt 组装 -> chat completion -> 返回答案**

## Day 11 要完成什么

今天只做问答最小闭环，不扩展问答记录和引用来源：

1. 设计第一版 system prompt
2. 设计基于检索结果的上下文拼装规则
3. 限制上下文长度，避免 prompt 失控
4. 复用现有 OpenAI-compatible client 调用 chat completion
5. 提供一个最小问答接口

## Day 11 第一版接口目标

建议今天先落一个最小可用接口：

`POST /api/knowledge-bases/{kbCode}/qa/ask`

请求至少包含：

1. `question`
2. `topK`

返回至少包含：

1. 原始问题
2. 最终答案
3. 实际使用的 `topK`
4. 使用的 chat model
5. 基础召回结果

## Day 11 的实现顺序

建议按下面顺序推进：

1. 先抽出 PromptBuilder 或最小拼装方法
2. 明确 system prompt 和 user prompt 模板
3. 将 Day 10 检索结果压缩成可控上下文
4. 接入 `OpenAiCompatibleClient.createChatCompletion`
5. 返回第一版问答响应结构
6. 补问答 service 单测

## Prompt 设计边界

Day 11 第一版 prompt 先遵守下面约束：

1. 只基于召回内容回答
2. 找不到答案时明确说信息不足
3. 不先做引用来源格式化
4. 不先做多轮对话

## Day 11 验收标准

今天结束时，至少要达到：

1. 可以针对指定知识库发起问题
2. 可以复用 Day 10 的召回结果构造上下文
3. 可以调用本地 chat completion 返回答案
4. 你能讲清楚 Day 11 的 Prompt 组装逻辑

## Day 11 完成后的项目状态

如果今天顺利完成，项目主链路会推进到：

```text
知识库创建 -> 文档上传 -> 解析 -> 切块 -> chunk 入库 -> chunk 向量写库 -> query embedding -> TopK 检索 -> Prompt 组装 -> LLM 回答
```

这意味着 Day 12 就可以直接进入：

**引用来源结构化返回。**
