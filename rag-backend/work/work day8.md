# Day 8：进入第 2 周

## 当前结论

Day 8 的任务已经完成。

Day 8 完成的不是“效果优化”，而是：

**把 Week 2 需要的本地 embedding 与向量存储地基真正搭起来。**

## Day 8 已完成

当前仓库在 Day 8 已经落下：

1. `rag.embedding / rag.llm / rag.retrieval` 配置入口
2. embedding 默认方案切到本地 `bge-small-zh-v1.5`
3. 本地 `embedding-service/` 服务代码
4. `docker-compose.yml` 中的 embedding 服务定义
5. PostgreSQL 切到 `pgvector`
6. `document_chunk` embedding 状态字段
7. `document_chunk.embedding_vector` 向量字段
8. `GET /api/knowledge-bases/{kbCode}/qa/readiness`
9. 本地模型加载验证
10. PostgreSQL collation mismatch 修复
11. Day 9 所需的本地 embedding 与 `pgvector` 前置条件已全部验证

## Day 8 收口

到 Day 8 结束时，项目已经具备下面这些 Week 2 前置条件：

1. 本地 embedding 技术路线已明确，不依赖 OpenAI API
2. `bge-small-zh-v1.5` 已能通过本地服务返回真实向量
3. PostgreSQL 已切到 `pgvector`
4. `document_chunk` 已具备向量字段和向量状态字段
5. Day 9 可以直接进入 chunk 向量写库与真实联调

## Day 8 之后

Day 8 之后最合理的推进顺序就是：

1. 先做 Day 9 chunk 向量写库
2. 再做 Day 10 TopK 检索
3. 然后接问答接口和引用来源
