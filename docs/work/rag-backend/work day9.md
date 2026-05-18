# Day 9：chunk 向量写库

## 当前结论

Day 9 的核心目标是：

**把已有 `document_chunk` 真实转换成向量，并写回 PostgreSQL。**

经过 Day 8，项目已经具备：

1. 本地 `bge-small-zh-v1.5` embedding 服务
2. `pgvector` PostgreSQL
3. `document_chunk.embedding_vector`
4. embedding 状态元数据字段

所以 Day 9 的工作顺序非常明确：

**读取 chunk -> 调用本地 embedding 服务 -> 写入 `embedding_vector` -> 更新 `embedding_status`**

## Day 9 已完成

当前仓库已经补上下面这些第一版实现：

1. `rag.embedding.batch-size` 配置项已增加
2. `OpenAiCompatibleClient` 已支持批量 embeddings 请求
3. `DocumentChunkMapper / Repository` 已支持更新 embedding 状态和向量
4. `DocumentEmbeddingService` 已新增
5. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/embed` 已新增
6. chunk 返回结构已补充 embedding 状态相关字段
7. `DocumentEmbeddingServiceTest` 已补入并通过
8. 真实文档 `/embed` 联调已成功
9. PostgreSQL 已验证 `embedding_vector` 非空
10. `document_chunk.embedding_status` 已成功更新为 `EMBEDDED`

## Day 9 联调结果

真实联调闭环已经跑通：

1. 读取 `document_chunk`
2. 批量调用本地 `bge-small-zh-v1.5` embedding 服务
3. 将向量以 `pgvector` literal 写入 `embedding_vector`
4. 回写 `embedding_status = EMBEDDED`

本次联调使用的真实文档是 `day6-kb / DOC-308871403707437056`，共 2 个 chunk，已全部写库成功。

## Day 9 还剩什么

Day 9 剩余的是补充和加固，不再是主链路可用性问题：

1. 验证重复执行时的行为是否符合预期
2. 设计按知识库批量回填 embedding 的能力
3. 为 Day 10 检索实现准备相似度查询接口

## 当前判断

Day 9 第一版链路已经跑通，项目已经从“能切块”正式进入“能向量化”阶段。

这意味着后面的 Day 10 检索工作不再需要回头补 embedding 主链路，而是可以直接在 `embedding_vector` 上做 TopK。
