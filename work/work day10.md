# Day 10：基础检索

## 当前结论

Day 10 的核心目标是：

**把用户问题真实转换成 query embedding，并基于 `pgvector` 完成第一版 TopK 召回。**

经过 Day 9，项目已经具备：

1. 本地 `bge-small-zh-v1.5` embedding 服务
2. `document_chunk.embedding_vector`
3. chunk 级 `embedding_status`
4. 真实文档 `/embed` 写库验证

所以 Day 10 的工作顺序非常明确：

**问题输入 -> query embedding -> pgvector 相似度检索 -> 返回 TopK chunk 与分数**

## Day 10 要完成什么

今天只做检索最小闭环，不做 LLM 问答扩展：

1. 新增问题检索请求结构
2. 复用现有 OpenAI-compatible embedding 客户端生成 query embedding
3. 在 `document_chunk` 上实现按知识库过滤的 TopK 相似度查询
4. 返回召回 chunk、文档信息和相似度分数
5. 提供最小检索接口，便于后续 Day 11 直接拼 Prompt

## Day 10 第一版接口目标

建议今天先落一个最小可用接口：

`POST /api/knowledge-bases/{kbCode}/qa/retrieve`

请求至少包含：

1. `question`
2. `topK`

返回至少包含：

1. 原始问题
2. 实际使用的 `topK`
3. embedding 模型
4. 召回结果列表
5. 每条结果的 `documentCode / chunkIndex / content / score`

## 检索阶段的边界

Day 10 先不做下面这些内容：

1. 不做 Prompt 组装
2. 不做大模型回答
3. 不做引用来源格式化
4. 不做问答记录持久化

这些内容分别留给：

1. Day 11：问答组装
2. Day 12：引用来源
3. Day 13：问答记录

## Day 10 验收标准

今天结束时，至少要达到：

1. 可以针对指定知识库输入问题
2. 可以生成 query embedding
3. 可以在 `pgvector` 上召回 TopK chunk
4. 返回结果里有基础分数和文档定位信息
5. 你能讲清楚 Day 10 的检索链路

## Day 10 完成后的项目状态

今天已经实际完成，项目主链路已经从：

```text
知识库创建 -> 文档上传 -> 解析 -> 切块 -> chunk 入库 -> chunk 向量写库
```

推进到：

```text
知识库创建 -> 文档上传 -> 解析 -> 切块 -> chunk 入库 -> chunk 向量写库 -> query embedding -> TopK 检索
```

这意味着 Day 11 就不需要再回头补检索底座，而是可以直接进入：

**Prompt 组装 + LLM 回答。**

## Day 10 实际完成情况

今天已经落地并验证：

1. 新增 `POST /api/knowledge-bases/{kbCode}/qa/retrieve`
2. 新增 `QuestionRetrievalRequest / QuestionRetrievalResponse`
3. `QuestionAnsweringService` 已完成 query embedding 调用
4. `DocumentChunkMapper` 已完成 `pgvector` TopK 查询
5. `QuestionAnsweringServiceTest` 已通过
6. `mvn -q -DskipTests compile` 已通过
7. `day6-kb` 真实联调已成功返回 2 条召回结果

## Day 10 联调结果

本次真实联调验证了下面这条链路：

**问题输入 -> query embedding -> 相似度检索 -> 返回 chunk 与 score**

联调使用：

1. 知识库：`day6-kb`
2. 问题：`这份文档主要讲了什么？`
3. `topK = 3`

联调结果：

1. `/qa/readiness` 返回 `questionAnsweringReady = true`
2. `/qa/retrieve` 返回 `hitCount = 2`
3. 命中结果来自 `DOC-308871403707437056`
4. 返回结果已包含 `documentCode / chunkIndex / content / score`
