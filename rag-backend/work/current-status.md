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
11. Day 18 的配置梳理与参数外置
12. Day 19 的切块参数对比实验
13. Day 20 的第一版中文问答评测资产与执行夹具
14. Day 21 的 Week 3 验收与文档收口
15. Day 22 的 Week 4 规划文档起步
16. Day 23 的第一版 hybrid retrieval 内核实现起步
17. Day 24 的 history 收口、前端适配与前后端联调完成
18. Day 25 的 dense vs hybrid 双轨评测资产收口
19. Day 26 的真实 dense vs hybrid 对比评测与补充样本验证完成
20. Day 27 的 hybrid retrieval 关键日志与最小耗时口径补齐
21. Day 28 的 Week 4 README、状态文档、RFC 与最终表达口径收口完成

当前项目已经不再停留在“能上传、能切块”的阶段，而是：

**本地 embedding 服务已跑通，`pgvector` 已就绪，Java 侧第一版问答链路已经完成真实端到端验收，Week 3 也已经完成第一版工程化收口。**

当前 Week 4 已经完成收口，但要特别说明一件事：

**Week 4 已经完成第一版实现、真实评测、最小观测与文档/RFC 收口；当前已确认 hybrid 在关键词密集题型上存在明确收益，但默认模式仍保持 `DENSE`。**

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
6. Spring Cache + Redis 业务缓存已接入
7. 已覆盖知识库、文档、chunk、`qa/readiness` 和检索结果读缓存

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
32. Spring Cache + Redis 已接入知识库、文档、chunk、`qa/readiness` 和检索结果缓存
33. `rag.executor / rag.chunking / rag.qa / rag.cache` 已开始接管线程池、切块、问答记录与缓存默认参数
34. 已具备第一版可重复执行的切块参数实验测试与长样本数据
35. 已补入中文评测样本文档、中文问答评测集与结果模板
36. `day20-cn-kb` 已完成第一版中文真实问答评测
37. README、`week3.md`、`work day21.md` 已完成 Week 3 收口
38. `week4.md` 已补入 Week 4 计划草案
39. `work day22.md` 已补入 Week 4 规划说明
40. `RetrievalMode`、第一版 keyword retrieval、`RRF fusion`、retrieval 配置扩展和 retrieval cache key 修正已落地
41. `/qa/retrieve` 与 `/qa/ask` 已支持可选 `retrievalMode`，并在响应中返回 `retrievalMode / fusionStrategy`
42. `qa/history` 已支持回放 `retrievalMode / fusionStrategy`，新历史记录按检索快照持久化，老数组格式继续兼容
43. 前端 retrieval / qa / history 页面已适配 `retrievalMode / fusionStrategy` 展示与模式切换
44. Day 25 已补入 dense vs hybrid 双轨评测问题集、结果模板和 runbook
45. Day 26 已完成两轮真实 dense vs hybrid 对比评测，并补入关键词密集补充样本
46. Day 27 已补齐 `qa.retrieve` 子阶段日志、`qa.ask.llm.completed` 和 retrieval / LLM / total 耗时字段
47. Day 28 已完成 README、`week4.md`、`current-status.md`、相关 RFC 与最终表达口径收口

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
28. `QaRecordServiceTest` 已验证问答记录配置外置后的实际生效行为
29. `mvn -q -DskipTests compile` 已验证 Redis 业务缓存接入后可正常编译
30. `ChunkingExperimentTest` 已完成 `compact / balanced / wide` 三组切块参数对比
31. `QaEvaluationDatasetTest` 已验证 Day 20 中文评测数据完整性
32. `day20-cn-kb` 已完成 6 条中文问题的真实问答评测，其中 5 条可回答问题命中预期文档
33. `mvn -q -Dtest=QuestionAnsweringServiceTest,QaServiceTest,QaRecordServiceTest,RedisCacheConfigTest test` 已通过 Day 23 改动验证
34. `mvn -q -Dtest=QaRecordServiceTest,QaServiceTest,QuestionAnsweringServiceTest test` 已通过 Day 24 改动验证
35. `npm run build` 已验证前端适配改动可正常构建
36. 已完成真实前后端联调：`DENSE/HYBRID retrieve`、`HYBRID ask`、`qa/history` 回放和 `5173` 代理路径均已验证
37. Day 26 已完成真实环境 dense vs hybrid 对比结果整理，并确认补充样本上存在明确收益
38. Day 26 补充样本中，`HYBRID` 在 `4/4` 关键词密集问题上完成正确召回与回答，`DENSE` 只有 `1/4` retrieval hit、`0/4` answer acceptable
39. `mvn -q -Dtest=QuestionAnsweringServiceTest,QaServiceTest,QaRecordServiceTest,RedisCacheConfigTest test` 已通过 Day 27 日志与响应字段改动验证
40. `mvn spring-boot:run` 已验证后端本地日志会自动写入 `rag-backend/logs/rag-service.log`

## 当前未完成

### 向量化

1. 向量回填的重复执行策略还需要再验证
2. 面向知识库级别或批量文档级别的 embedding 编排还未开始

### 工程化补充

1. 异步索引任务编排已完成第一版起步，失败重试与恢复已落地，但任务取消和批量编排仍未开始
2. OpenAPI / Swagger 未开始
3. 更完整的日志采集、指标平台和 tracing 仍未开始，目前只完成了最小可排障口径
4. 第一版 hybrid retrieval 已完成真实环境对比评测与最小观测收口，但更细的召回抑制、延迟对比和默认模式切换仍未完成
5. 当前检索结果缓存采用整缓存清理策略，后续可以细化到知识库级别
6. Week 4 评测集已经扩展并完成两轮对比，但还需要继续扩大到专有名词、接口名和错误码样本

## 当前判断

当前阶段的真实结论已经明确：

1. Week 1 已完成
2. Week 2 已完成第一版收口
3. Week 3 已完成第一版收口
4. Day 15 已完成第一版异步索引能力
5. Day 16 已完成第一版失败重试与恢复能力
6. Day 17 已完成第一版结构化日志能力
7. Day 18 已完成第一版配置梳理与参数外置
8. Redis 业务缓存已完成第一版接入
9. Day 19 已完成第一版切块参数对比实验
10. Day 20 已完成第一版中文问答评测资产与执行夹具
11. Day 20 已完成第一版中文真实问答评测
12. Day 21 已完成 README、状态文档与 Week 3 验收口径收口
13. Day 22 已补入 Week 4 的混合检索、评测体系与可观测性规划说明
14. Day 23 已完成第一版 hybrid retrieval 内核起步，包括 keyword retrieval、RRF fusion 和 retrievalMode 缓存隔离
15. Day 24 已完成 history 快照兼容、前端适配和真实前后端联调，`retrieve / ask / history` 三条链路对 `retrievalMode / fusionStrategy` 的表达已经统一
16. Day 27 已完成 `retrieve / ask` 的关键阶段日志与最小耗时字段，当前已经可以把 retrieval 与 LLM 两层代价拆开观察
17. Day 28 已完成 Week 4 README、状态文档、RFC 与最终表达口径收口，当前项目资料已与真实代码状态重新对齐
18. 当前系统已经具备最小可用的 RAG 问答闭环，并开始进入 Week 4 之后的检索增强阶段
19. 当前 Week 4 已完成真实对比评测、补充样本验证与最小观测收口，可以把 hybrid 视为“已完成第一版验收，但默认模式仍保守保留为 `DENSE`”的能力

## 后续方向

如果后续继续推进，建议按下面顺序继续：

1. 继续扩大评测集，并重点补专有名词、接口名、错误码和更长字段名样本
2. 基于当前最小观测口径补真实延迟对比，并决定是否有条件切默认模式
3. 优化召回质量、无答案场景抑制和答案质量
4. 增加 session 复用与多轮对话
5. 继续补齐任务取消、批量编排、日志平台和 tracing 能力
