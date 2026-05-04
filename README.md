# RAG Service

一个面向企业内部知识库场景的 RAG 后端服务，当前聚焦结算领域文档的沉淀、检索、问答、来源返回和问答记录。

当前仓库的真实阶段不是“设计中”，而是已经完成了前 3 周的第一版实现：

1. Week 1：文档入库主链路完成
2. Week 2：检索与问答主链路完成
3. Week 3：异步索引、恢复、日志、配置和评测完成第一版收口

## 项目目标

这个项目解决的是企业内部知识分散、检索成本高、经验难沉淀的问题，不做泛化聊天机器人，优先把最小可用 RAG 服务做完整。

当前主链路：

```text
知识库创建 -> 文档上传 -> 异步索引 -> 解析 -> 切块 -> 向量写库 -> 检索 -> 问答 -> 来源返回 -> 问答记录
```

## 当前完成情况

### 已实现能力

1. 知识库创建、列表、详情、启用、禁用
2. 文档上传、列表、详情、chunk 查询、禁用、处理、重处理
3. `md / txt / pdf` 第一版解析
4. 第一版固定窗口切块与 chunk 入库
5. 本地文件存储与内容去重
6. `pgvector` 向量写库
7. query embedding 与 TopK 检索
8. 基于 OpenAI-compatible 协议的 LLM 问答
9. `sources` 结构化来源返回
10. 问答记录持久化与历史查询
11. 文档异步索引、任务状态追踪、手动重试
12. 卡住索引任务的定时恢复扫描
13. 基于 Redis 的第一版业务缓存，已覆盖知识库、文档读取、chunk 列表、`qa/readiness` 和检索结果短 TTL 缓存
14. 请求、异常、异步索引、问答链路的第一版结构化日志
15. `rag.executor / rag.chunking / rag.qa / rag.embedding / rag.llm / rag.retrieval / rag.indexing / rag.cache` 配置外置
16. 第一版切块参数实验
17. 第一版中文问答评测样本、问题集、结果模板和真实评测记录

### 已完成验证

1. PostgreSQL、Redis、Flyway 可正常工作
2. `md / txt / pdf` 三类样本文档已完成真实联调
3. 本地 `bge-small-zh-v1.5` embedding 服务已返回真实 512 维向量
4. `POST /embed` 已完成真实文档向量写库
5. `POST /qa/retrieve` 已返回真实 TopK 结果
6. `POST /qa/ask` 已完成真实联调
7. `GET /qa/history` 已查回真实问答记录
8. `day14-kb` 已完成从上传到问答历史的端到端验收
9. Redis 业务缓存已接入知识库、文档、chunk、`qa/readiness` 和检索结果读路径
10. `day20-cn-kb` 已完成 6 条中文问题的真实评测，其中 5 条可回答问题命中预期文档，1 条无答案问题返回兜底话术

### 当前边界

1. 还没有做多实例任务协调、任务取消和批量索引编排
2. 还没有做混合检索、重排序和更细的召回抑制
3. 还没有做 session 复用与多轮对话
4. 还没有补齐完整监控、指标和 tracing
5. 评测集还处在第一版，规模和覆盖度都需要继续扩展

## 周进度

### Week 1

1. 项目边界、目录结构和 README 大纲完成
2. 核心表与状态模型落地到 Flyway
3. Spring Boot、PostgreSQL、Redis、统一响应、异常处理完成
4. 文档上传、本地落盘、去重、元数据入库完成
5. `md / txt / pdf` 第一版解析、切块、chunk 入库完成
6. Day 6 真实联调和问题修正完成
7. Day 7 文档与架构口径收口完成

### Week 2

1. 本地 embedding 服务接入
2. `pgvector` 落地
3. `POST /qa/retrieve` 检索接口完成
4. `POST /qa/ask` 问答接口完成
5. `sources` 来源返回完成
6. `chat_session / chat_message` 与 `/qa/history` 完成
7. Day 14 端到端验收完成

### Week 3

1. `POST /documents/{documentCode}/index` 异步索引入口完成
2. `GET /documents/{documentCode}/indexing-tasks` 任务查询完成
3. `POST /indexing-tasks/{taskId}/retry` 手动重试完成
4. 卡住任务自动恢复扫描完成
5. Redis 业务缓存完成第一版接入
6. 结构化日志完成第一版接入
7. 线程池、切块、问答记录与缓存参数完成配置外置
8. `compact / balanced / wide` 切块参数实验完成
9. 中文问答评测样本、问题集、夹具与首轮真实评测完成

## 技术选型

- Java 17
- Spring Boot 3.5.14
- Spring Web
- Spring Validation
- MyBatis-Plus
- PostgreSQL + `pgvector`
- Redis
- Flyway
- Spring Boot Actuator
- PDFBox
- OpenAI-compatible HTTP 集成

当前设计取舍很明确：

1. 第一阶段优先用 PostgreSQL 统一承载主数据、任务数据和向量数据
2. embedding 单独做本地 HTTP 服务，降低 Java 主服务耦合
3. 先把单服务版本做完整，再考虑更复杂的编排和检索优化

## 项目结构

```text
rag-system/
├── docker-compose.yml
├── pom.xml
├── embedding-service/              # 本地 embedding 服务
├── src/main/java/com/example/rag/
│   ├── common/
│   ├── config/
│   ├── controller/
│   ├── generation/
│   ├── ingestion/
│   │   ├── chunk/
│   │   ├── parser/
│   │   └── storage/
│   ├── integration/llm/
│   ├── mapper/
│   ├── model/
│   ├── persistence/
│   ├── repository/
│   ├── retrieval/
│   └── service/
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── db/migration/
├── src/test/java/com/example/rag/
│   ├── evaluation/
│   ├── ingestion/chunk/
│   └── service/
└── work/                           # 周计划、阶段记录、评测文档
```

## 核心数据

### 主要表

1. `knowledge_base`
2. `document`
3. `document_chunk`
4. `indexing_task`
5. `chat_session`
6. `chat_message`

### 已落地迁移

1. `V1__init_schema.sql`
2. `V4__create_document_chunk_table.sql`
3. `V5__create_indexing_task_table.sql`
4. `V6__add_chunk_embedding_metadata.sql`
5. `V7__enable_pgvector_and_add_chunk_vector.sql`
6. `V8__create_chat_tables.sql`
7. `V9__add_async_indexing_task_fields.sql`
8. `V10__add_indexing_retry_and_recovery_fields.sql`

## 运行方式

### 1. 启动依赖

```bash
docker compose up -d postgres redis embedding-service
```

默认端口：

1. PostgreSQL：`5432`
2. Redis：`6379`
3. Embedding Service：`8001`

### 2. 启动应用

```bash
mvn spring-boot:run
```

### 3. 健康检查

```bash
curl --noproxy '*' -s http://127.0.0.1:8080/api/health
curl --noproxy '*' -s http://127.0.0.1:8080/api/health/redis-probe
curl --noproxy '*' -s http://127.0.0.1:8001/health
```

## 关键配置

`src/main/resources/application.yml` 当前已经整理出这些主配置域：

1. `rag.storage.*`
2. `rag.executor.*`
3. `rag.chunking.*`
4. `rag.embedding.*`
5. `rag.llm.chat.*`
6. `rag.retrieval.*`
7. `rag.qa.*`
8. `rag.indexing.*`
9. `rag.cache.*`

默认值里当前最重要的几项：

1. embedding 模型：`bge-small-zh-v1.5`
2. 向量维度：`512`
3. 默认切块：`600/80/240`
4. 默认检索 `topK`：`5`
5. 最大索引重试次数：`3`
6. 检索结果短 TTL 缓存：`60s`

## 当前接口

### 健康与观察

1. `GET /api/health`
2. `GET /api/health/redis-probe`
3. `GET /api/knowledge-bases/{kbCode}/qa/readiness`

### 知识库

1. `POST /api/knowledge-bases`
2. `GET /api/knowledge-bases`
3. `GET /api/knowledge-bases/{kbCode}`
4. `POST /api/knowledge-bases/{kbCode}/disable`
5. `POST /api/knowledge-bases/{kbCode}/enable`

### 文档

1. `POST /api/knowledge-bases/{kbCode}/documents/upload`
2. `GET /api/knowledge-bases/{kbCode}/documents`
3. `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}`
4. `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/chunks`
5. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/process`
6. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/reprocess`
7. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/embed`
8. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index`
9. `GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks`
10. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks/{taskId}/retry`

### 问答

1. `POST /api/knowledge-bases/{kbCode}/qa/retrieve`
2. `POST /api/knowledge-bases/{kbCode}/qa/ask`
3. `GET /api/knowledge-bases/{kbCode}/qa/history`

## 验收样例

### Day 14 端到端验收

`day14-kb` 已完成：

1. 创建知识库
2. 上传文档
3. 文档处理
4. 文档向量化
5. 检索
6. 问答
7. 历史查询

### Day 20 中文评测

`day20-cn-kb` 已完成第一版中文真实问答评测：

1. 固定口径：`zh-CN`、`topK=3`
2. 覆盖类型：`FACT / SUMMARY / PROCESS / NO_ANSWER`
3. 结果：`5/5` 可回答问题命中预期文档
4. 无答案问题返回“根据当前检索内容，无法确定答案。”

## 后续方向

下一阶段更适合继续推进这些事情：

1. 扩大评测集并沉淀稳定评分口径
2. 做无答案场景的召回抑制
3. 增加混合检索与重排序
4. 增加 session 复用与多轮对话
5. 补齐任务取消、批量编排和更完整观测能力

## 相关文档

1. [当前状态](/root/workspace/rag-system/work/current-status.md)
2. [Week 1](/root/workspace/rag-system/work/week1.md)
3. [Week 2](/root/workspace/rag-system/work/week2.md)
4. [Week 3](/root/workspace/rag-system/work/week3.md)
5. [Day 20 评测记录](/root/workspace/rag-system/work/work%20day20.md)
6. [Day 21 收口说明](/root/workspace/rag-system/work/work%20day21.md)
