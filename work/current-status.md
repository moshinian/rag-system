# 当前状态

## 当前结论

当前工程已经完成：

1. Week 1 文档入库主链路
2. Day 8 的 Week 2 技术起步
3. Day 9 的第一版 chunk 向量写库联调
4. Day 10 的第一版 query embedding 与 TopK 检索联调
5. Day 11 的第一版 Prompt 组装与问答联调
6. Day 13 的第一版问答记录持久化与历史查询联调
7. Day 14 的 Week 2 端到端验收
8. Day 15 的异步索引与任务状态追踪起步
9. Day 16 的失败重试与任务恢复
10. Day 17 的结构化日志

当前项目已经不再停留在“能上传、能切块”的阶段，而是：

**本地 embedding 服务已跑通，`pgvector` 已就绪，Java 侧第一版问答链路已经完成真实端到端验收，Week 2 已完成第一版收口，Week 3 已开始补异步索引工程能力。**

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
10. Java 侧已新增 `POST /api/knowledge-bases/{kbCode}/qa/retrieve`
11. Java 侧已完成 query embedding 调用
12. `document_chunk` 已支持按知识库执行 TopK 相似度查询
13. 检索结果已返回 `documentCode / chunkIndex / content / score`
14. Java 侧已新增 `POST /api/knowledge-bases/{kbCode}/qa/ask`
15. `QaService / PromptBuilder / ChatClient` 已落地
16. `rag.llm.chat.*` 已支持基于 OpenAI-compatible 协议切换不同提供方
17. `chat_session / chat_message` 已落地
18. `GET /api/knowledge-bases/{kbCode}/qa/history` 已落地
19. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index` 已落地
20. `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks` 已落地
21. `indexing_task` 已支持 `QUEUED / RUNNING / SUCCEEDED / FAILED`
22. `indexing_task` 已支持 `task_stage / embedded_chunk_count`
23. 后台 `DOCUMENT_INDEXING` 任务已串起 `process + embed`
24. `DocumentEmbeddingService` 已支持循环处理整篇文档的多批次 chunk
25. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks/{taskId}/retry` 已落地
26. `indexing_task` 已支持 `parent_task_id / trigger_source / retry_count / max_retry_count / last_heartbeat_at / recovered_at`
27. 失败任务会生成子任务执行手动重试
28. 卡住的 `QUEUED / RUNNING` 任务已支持定时恢复扫描
29. 当前索引恢复已具备最大重试次数边界
30. 请求开始/结束、异常、异步索引、问答链路已接入第一版结构化日志
31. `requestId` 已通过 `MDC` 进入异步索引线程

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
15. `mvn -q -DskipTests compile` 已通过
16. `mvn -q -Dtest=QuestionAnsweringServiceTest test` 已通过
17. `GET /qa/readiness` 已验证 `day6-kb` 的 Day 10 前置条件已满足
18. `POST /qa/retrieve` 已在 `day6-kb` 上返回真实 TopK 结果
19. `mvn -q -Dtest=QaServiceTest,QuestionAnsweringServiceTest test` 已通过
20. `POST /qa/ask` 已通过 DeepSeek `deepseek-v4-pro` 完成真实联调
21. `POST /qa/ask` 已验证成功落库
22. `GET /qa/history` 已验证可以查回真实问答记录
23. `day14-kb` 已完成从上传到问答历史的完整端到端验收
24. 无关问题场景已验证返回“根据当前检索内容，无法确定答案。”
25. `DocumentIndexingServiceTest / DocumentEmbeddingServiceTest / DocumentProcessingServiceTest` 已覆盖 Day 15 关键分支
26. `mvn -q -Dtest=DocumentIndexingServiceTest,DocumentEmbeddingServiceTest,DocumentProcessingServiceTest test` 已通过 Day 16 改动验证
27. `StructuredLogMessageTest` 已覆盖结构化日志消息格式

## 当前未完成

### 向量化

1. 向量回填的重复执行策略还需要再验证
2. 面向知识库级别或批量文档级别的 embedding 编排还未开始

### 工程化补充

1. 异步索引任务编排已完成第一版起步，失败重试与恢复已落地，但任务取消和批量编排仍未开始
2. OpenAPI / Swagger 未开始
3. 更完整的日志采集、指标和 tracing 仍未开始
4. 评测集未开始

## 当前判断

当前阶段的真实结论已经明确：

1. Week 1 已完成
2. Week 2 已完成第一版收口
3. Week 3 已开始，Day 15 已完成第一版异步索引能力
4. Day 16 已完成第一版失败重试与恢复能力
5. Day 17 已完成第一版结构化日志能力
6. 当前系统已经具备最小可用的 RAG 问答闭环，并开始补工程化能力

## 后续方向

如果后续继续推进，建议按下面顺序继续：

1. 建立评测集并做效果评测
2. 优化召回质量与答案质量
3. 增加 session 复用与多轮对话
4. 继续补齐异步编排、日志和观测能力
