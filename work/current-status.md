# 当前状态

## 当前结论

当前工程已经完成：

1. Week 1 文档入库主链路
2. Day 8 的 Week 2 技术起步
3. Day 9 的第一版 chunk 向量写库联调

当前项目已经不再停留在“能上传、能切块”的阶段，而是：

**本地 embedding 服务已跑通，`pgvector` 已就绪，Java 侧第一版 chunk embedding 写库链路已经完成真实端到端验证。**

## 已完成

### 工程基础

1. Spring Boot 3 + Java 17 工程已搭好
2. 包结构已按 `controller / service / repository / model / ingestion / integration / common / config` 拆分
3. 统一响应结构已完成
4. 全局异常处理已完成
5. 请求级 `X-Request-Id` 已接入
6. 健康检查接口已完成
7. Redis 探针接口已完成
8. 基础线程池配置已完成
9. Actuator 已接入

### PostgreSQL

1. PostgreSQL 已真实连通
2. Flyway 已接入
3. MyBatis-Plus Mapper 与 `persistence` 封装已打通
4. PostgreSQL 已切换到 `pgvector/pgvector:pg16`
5. `vector` 扩展已在 `rag_db` 中可用
6. `document_chunk.embedding_vector` 已落库
7. PostgreSQL `collation version mismatch` 已处理

### Redis

1. Redis 依赖已接入
2. Redis 配置已补齐
3. Redis 已配置密码并与本地容器对齐
4. `StringRedisTemplate` 已接入
5. 最小 `set/get` 验证能力已落地

### 文档上传与处理

1. 知识库创建链路可运行
2. 文档上传入库链路可运行
3. `md / txt / pdf` 第一版解析与切块链路已落地
4. `indexing_task` 独立处理记录已落地
5. Day 6 真实联调、字段校验与问题修正已完成
6. Day 7 README、阶段文档与架构口径已对齐

### Embedding 与向量化

1. 本地 `bge-small-zh-v1.5` embedding 服务已落地
2. 本地模型目录已接入容器加载
3. embedding 服务健康状态已验证
4. embedding 服务真实请求已返回 512 维向量
5. `document_chunk` 已补齐 `embedding_status / embedding_model / embedding_error_message / embedding_updated_at / embedding_vector`
6. `qa/readiness` 观察接口已落地
7. Java 侧已新增第一版 `DocumentEmbeddingService`
8. Java 侧已新增 `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/embed`
9. 真实文档 chunk 已完成 embedding 写库并更新为 `EMBEDDED`

## 已验证

已经做过实际验证的内容：

1. Spring Boot 服务可编译
2. PostgreSQL 连接成功
3. Redis 连接成功
4. Flyway 迁移成功
5. 知识库创建接口写库成功
6. 文档上传接口写库成功
7. `md / txt / pdf` 三类样本文档 Day 6 联调已通过
8. 本地 `bge-small-zh-v1.5` 模型已成功加载
9. embedding 服务真实请求已返回向量
10. `vector` 扩展和 embedding 列已在当前数据库中可查询
11. `DocumentEmbeddingServiceTest` 已通过
12. `QuestionAnsweringServiceTest / KnowledgeBaseServiceTest / DocumentServiceTest` 已通过
13. `POST /embed` 已完成真实文档联调
14. `document_chunk.embedding_vector` 已验证非空

## 当前未完成

### 向量化

1. 向量回填的重复执行策略还需要再验证
2. 面向知识库级别或批量文档级别的 embedding 编排还未开始

### 检索与问答

1. TopK 向量检索未开始
2. 大模型问答未开始
3. 引用来源展示未开始
4. 问答记录未开始

### 工程化补充

1. 异步索引任务编排未开始
2. OpenAPI / Swagger 未开始
3. 更完整的结构化日志未开始
4. 评测集未开始

## 当前判断

当前阶段的真实优先级已经非常明确：

1. 进入 Day 10 TopK 检索
2. 设计相似度查询接口和返回结构
3. 再进入问答链路
4. 之后再进入评测和优化

## 下一步建议

建议按下面顺序继续：

1. 实现基础 TopK 相似度检索
2. 设计 query embedding 调用和检索 service
3. 再接问答接口和引用来源结构
4. 最后补评测与优化
