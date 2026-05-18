# RAG System

一个面向企业内部知识库场景的 RAG 系统仓库，包含 Spring Boot 后端、React 前端，以及本地开发用的 PostgreSQL / Redis 依赖编排。当前聚焦结算领域文档的沉淀、检索、问答、来源返回和问答记录。

当前仓库还新增了独立的 `rag-ai-service` Python FastAPI 模块，作为 Java 主系统前面的最小 AI Gateway，用于统一封装 embeddings 和 chat completions 调用。

当前仓库的真实阶段不是“设计中”，而是已经完成了前 4 周的第一版实现；Week 4 已完成第一版 hybrid retrieval、真实评测与最小观测收口：

1. Week 1：文档入库主链路完成
2. Week 2：检索与问答主链路完成
3. Week 3：异步索引、恢复、日志、配置和评测完成第一版收口
4. Week 4：混合检索已完成第一版内核、真实评测、最小观测收口，并形成稳定结论

## 项目目标

这个项目解决的是企业内部知识分散、检索成本高、经验难沉淀的问题，不做泛化聊天机器人，优先把最小可用 RAG 服务做完整。

当前主链路：

```text
知识库创建 -> 文档上传 -> 异步索引 -> 解析 -> 切块 -> 向量写库 -> 检索 -> 问答 -> 来源返回 -> 问答记录
```

## 当前完成情况

### 已实现能力

1. 知识库创建、列表、详情、启用、禁用、直接删除
2. 文档上传、列表、详情、chunk 查询、禁用、恢复、处理、重处理
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
18. embedding profile 变化后的全量重嵌入后台任务
19. 知识库手工恢复使用，以及“恢复并重试失败索引任务”运维动作
20. 前端知识库工作台已补入恢复、重嵌入和返回知识库列表入口
21. 文档级软禁用/恢复，禁用后历史 chunk 与向量保留但不再参与检索口径
22. Redis 坏缓存读失败自愈，以及异步索引提交阶段的孤儿 `QUEUED` 任务兜底
23. 系统健康页已覆盖 PostgreSQL、Redis、embedding 接口和 LLM 接口的可用性检查
24. Week 4 相关计划文档已补入，并已落下第一版 hybrid retrieval 内核起步
25. `qa/retrieve` 已支持 `DENSE / HYBRID` 两种检索模式，第一版 `HYBRID` 采用 `dense recall + keyword recall + RRF fusion`
26. `rag.retrieval` 已补入 `defaultMode / denseCandidateLimit / keywordCandidateLimit / fusionK / keywordMinTokenLength`
27. 检索结果缓存 key 已纳入 `retrievalMode`，避免 dense 与 hybrid 串缓存
28. `qa/history` 已支持回放 `retrievalMode / fusionStrategy`，并兼容老的 `retrievedChunks` 数组快照
29. 前端 retrieval / qa / history 页面已适配 `retrievalMode / fusionStrategy` 展示与模式切换
30. Day 26 已完成两轮真实 `DENSE vs HYBRID` 对比评测：基线样本整体结果接近，但补充样本已确认 hybrid 在关键词密集题型上存在明确收益
31. Day 27 已补齐 `qa.retrieve` 子阶段日志、`qa.ask.llm.completed` 和 retrieval / LLM / total 耗时字段
32. 后端启动与运行日志现已默认落盘到 `rag-backend/logs/`，便于本地长期排障和回看
33. `rag.retrieval.keywordStrategy` 已支持 `LIKE / POSTGRES_FTS` 两种内部 lexical strategy，默认仍为 `LIKE`
34. 已完成真实 `POSTGRES_FTS` 运行验证；当前 `day20-cn-kb` 中文样本上尚未观察到稳定 lexical 增益，因此仍不作为默认策略
35. `POSTGRES_FTS` 在 CJK 查询零命中时会自动回退到 `LIKE` keyword recall`，避免出现“LIKE 可命中、FTS 完全空”的体验倒挂

### 已完成验证

1. PostgreSQL、Redis、Flyway 可正常工作
2. `md / txt / pdf` 三类样本文档已完成真实联调
3. 远端 OpenAI-compatible embedding 接口已完成接入与真实向量返回
4. `POST /embed` 已完成真实文档向量写库
5. `POST /qa/retrieve` 已返回真实 TopK 结果
6. `POST /qa/ask` 已完成真实联调
7. `GET /qa/history` 已查回真实问答记录
8. `day14-kb` 已完成从上传到问答历史的端到端验收
9. Redis 业务缓存已接入知识库、文档、chunk、`qa/readiness` 和检索结果读路径
10. `day20-cn-kb` 已完成 6 条中文问题的真实评测，其中 5 条可回答问题命中预期文档，1 条无答案问题返回兜底话术
11. 知识库恢复使用、恢复并重试失败任务、重新嵌入入口已完成前后端联动
12. 前端生产构建已完成路由级懒加载与 vendor 拆包，不再是单一超大入口包
13. 文档禁用/恢复已完成前后端联动，文档恢复后重新计入 readiness 口径
14. Redis 不可反序列化缓存值已完成真实自愈验证，不再因为脏缓存直接返回 500
15. 评测数据集、切块实验样本和 PDF 样本测试路径已统一收口，`mvn test` 已在当前仓库状态下全量通过
16. `QuestionAnsweringServiceTest / QaServiceTest / QaRecordServiceTest / RedisCacheConfigTest` 已通过 Day 23 改动验证
17. `qa/history` 新旧快照兼容测试已通过，前端生产构建已通过
18. 已完成一次真实前后端联调：后端直连、Vite 代理、`DENSE/HYBRID retrieve`、`HYBRID ask` 与 history 回放都已验证
19. Week 4 已完成第一版 hybrid retrieval 的真实对比评测与最小观测收口；当前结论是关键词密集题型存在明确收益，但默认模式继续保留 `DENSE`
20. 2026-05-14 已完成一次真实 `POSTGRES_FTS` 联调；运行正常，但在当前中文评测问题上 `keywordHitCount` 未出现稳定收益
21. 2026-05-14 已补入 `POSTGRES_FTS -> LIKE` 的 CJK 零命中兜底；真实体验结论仍然是默认 `LIKE` 更稳妥

### 当前边界

1. 还没有做多实例任务协调、任务取消和批量索引编排
2. 第一版 hybrid retrieval 已完成真实对比评测与最小观测收口，并确认在关键词密集题型上存在收益，但更细的召回抑制和默认模式切换仍未完成
3. `POSTGRES_FTS` 已作为可配置 lexical strategy 接入，但在当前 `simple` 配置和 `day20-cn-kb` 中文样本下仍未证明比默认 `LIKE` 更优
4. 对“第二百三十八条的”这类中文条文短语，当前 PostgreSQL FTS 默认词法处理仍弱于 `LIKE` 子串匹配，因此 CJK 零命中时已回退到 `LIKE`
5. 还没有做 session 复用与多轮对话
6. 还没有补齐完整监控、指标和 tracing
7. 评测集还处在第一版，规模和覆盖度都需要继续扩展
8. 前端已做第一轮拆包优化，但还没有继续做更细的业务级按需加载

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

1. OpenAI-compatible embedding 接入
2. `pgvector` 落地
3. `POST /qa/retrieve` 检索接口完成
4. `POST /qa/ask` 问答接口完成
5. `sources` 来源返回完成
6. `chat_session / chat_message` 与 `/qa/history` 完成
7. Day 14 端到端验收完成
8. embedding rebuild、readiness gate 和知识库恢复语义已落地

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
10. embedding rebuild、知识库恢复补偿和前端运维入口完成
11. 文档软禁用/恢复、缓存自愈和索引提交兜底完成

### Week 4

1. `week4.md` 已明确以混合检索为主线
2. `work day22.md` 已完成 Week 4 起步与影响面说明
3. Day 23 已完成 keyword retrieval、RRF fusion、retrievalMode 配置和缓存键修正的第一版实现
4. Day 24 已完成 `qa/history` 快照兼容、前端适配与真实前后端联调
5. Day 25 已完成 dense vs hybrid 双轨评测问题集、结果模板和执行口径
6. Day 26 已完成真实对比评测，并通过补充样本确认 hybrid 在关键词密集题型上的收益
7. Day 27 已完成检索与问答关键日志、最小耗时字段和本地日志落盘
8. Day 28 已完成 README、状态文档、RFC 与 Week 4 表达口径统一收口
9. Day 29 已完成 `LIKE / POSTGRES_FTS` 双 lexical strategy 接入、真实联调、运行时问题修复，以及 CJK 零命中 fallback 收口

### Week 4 最终结论

Week 4 结束后，当前项目可以稳定表达为：

1. 第一版 `HYBRID` 已采用 `dense recall + keyword recall + RRF fusion` 跑通真实主链路；
2. `qa/retrieve / qa/ask / qa/history` 已统一表达 `retrievalMode / fusionStrategy` 和检索证据快照；
3. 两轮真实 `DENSE vs HYBRID` 对比评测已经确认：收益存在，但主要集中在关键词密集、ASCII term、document lookup 题型；
4. 检索与问答链路已经具备第一版最小观测口径，可区分 dense、keyword、fusion、LLM 各阶段耗时；
5. 默认模式继续保留 `DENSE`，因为更大样本和延迟成本还没有完成下一阶段正式验收。
6. `POSTGRES_FTS` 已作为内部可配置 lexical strategy 跑通，但在当前中文评测样本上未观察到稳定 keyword gain，默认 lexical strategy 继续保留 `LIKE`。
7. 当前更准确的工程判断是：`POSTGRES_FTS` 更适合继续作为实验性同库 lexical 路线保留，而不是替换默认 `LIKE`。

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
- Python FastAPI AI Gateway

当前设计取舍很明确：

1. 第一阶段优先用 PostgreSQL 统一承载主数据、任务数据和向量数据
2. Java 主服务统一通过 `rag-ai-service` 调用 OpenAI-compatible 模型能力，降低业务系统对具体 provider 的耦合
3. 先把单服务版本做完整，再考虑更复杂的编排和检索优化
4. 混合检索先采用 PostgreSQL 内 keyword recall + RRF 的轻量路线，先把效果对比和观测口径站稳，再决定是否继续引入更重基础设施
5. `POSTGRES_FTS` 当前只作为同库内的实验性 lexical strategy；在中文场景收益未被真实评测确认前，不替换默认 `LIKE`
6. 对中文条文、法规编号后缀变化、短短语 document lookup 等 case，默认 `LIKE` 目前比 `POSTGRES_FTS(simple)` 更稳

## 项目结构

```text
rag-system/
├── pom.xml                         # 根聚合 POM，便于 VS Code / Maven 识别工作区中的 Java 模块
├── docker-compose.yml              # PostgreSQL / Redis / RedisInsight 本地依赖编排
├── rag-system.code-workspace       # VS Code 推荐工作区
├── .vscode/                        # VS Code 任务、启动配置、Java 导入设置
├── rag-backend/                    # Spring Boot 后端工程
│   ├── pom.xml                     # 后端 Maven 构建文件
│   ├── maven-settings.xml          # Maven 镜像配置
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/rag/
│   │   │   │   ├── common/         # 统一响应、错误码、异常、ID、结构化日志
│   │   │   │   ├── config/         # Redis、线程池、请求 ID、配置属性
│   │   │   │   ├── controller/     # 健康、知识库、文档、问答 HTTP 接口
│   │   │   │   ├── ingestion/      # 文档解析、切块、文件存储
│   │   │   │   ├── integration/    # 外部 LLM / embedding HTTP 集成
│   │   │   │   ├── mapper/         # MyBatis-Plus Mapper
│   │   │   │   ├── model/          # request / response / dto / enum
│   │   │   │   ├── persistence/    # Repository、Entity、分页查询
│   │   │   │   └── service/        # 业务主链路：上传、处理、索引、检索、问答
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-local.yml
│   │   │       └── db/migration/   # Flyway 迁移脚本
│   │   └── test/                   # 单元测试、集成测试、评测夹具
│   ├── data/uploads/               # 本地文档上传落盘目录
│   └── work/                       # 周计划、阶段记录、样本文档、评测结果
├── rag-ai-service/                 # Python FastAPI AI Gateway
│   ├── pyproject.toml
│   ├── Dockerfile
│   ├── app/
│   │   ├── api/                    # /health /v1/embeddings /v1/chat/completions
│   │   ├── clients/                # 上游 OpenAI-compatible provider client
│   │   ├── core/                   # 配置、异常、requestId middleware
│   │   ├── models/                 # OpenAI-compatible request/response models
│   │   └── services/               # gateway 编排与日志
│   └── tests/                      # FastAPI 接口测试
├── rag-frontend/                   # React 前端工程
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── src/
│   │   ├── api/                    # 后端接口封装
│   │   ├── app/                    # Router、Store、Provider
│   │   ├── components/             # 卡片、表格、反馈、来源查看器等复用组件
│   │   ├── hooks/                  # 当前知识库、轮询、API 错误处理
│   │   ├── pages/                  # dashboard / documents / qa / retrieval / history / health
│   │   ├── styles/                 # 全局样式
│   │   ├── types/                  # 前端类型声明
│   │   └── utils/                  # 格式化、状态映射
│   └── dist/                       # 前端生产构建产物
├── docs/
│   ├── rfcs/                       # 长期工程决策
│   └── work/                       # 各板块状态、计划与推进记录
```

### 结构说明

1. 根目录现在保留了聚合 `pom.xml`，目的是让 VS Code Java / Spring Boot Dashboard 在只打开仓库根目录时也能识别 `rag-backend` 模块。
2. `rag-system.code-workspace` 当前只挂载仓库根目录，避免把根目录和 `rag-backend` / `rag-frontend` 重复加入 workspace 后造成 Java 项目重复导入。
3. 各板块的工作文档已统一迁移到 `docs/work/`，按 `README + current-status + plan + history` 组织。
4. `docs/work/rag-backend/`、`docs/work/rag-frontend/` 和 `docs/work/rag-ai-service/` 现在分别承接各自板块的状态、计划和推进记录。

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
9. `V14__embedding_rebuild_state.sql`
10. `V15__drop_legacy_vector_index.sql`
11. `V16__add_document_disabled_from_status.sql`

## 最近补充

### 知识库恢复与失败任务补偿

当前知识库不会因为切片或 embedding 失败自动进入 `INACTIVE`。`INACTIVE` 仍然是手工运维状态。

现在后端已经支持：

1. 仅恢复知识库使用状态
2. 恢复知识库并重试每篇文档最近一次可重试的失败索引任务

前端知识库列表页和知识库概览页都已经补入这些操作入口。

### 文档软禁用与恢复

当前文档级生命周期采用“软下线”语义，而不是物理删除：

1. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/disable`
   把文档切为 `DISABLED`，历史原文件、chunk 和向量都保留，但不会再计入 `qa/readiness` 的可检索切块/向量统计，也不会参与检索和问答。
2. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/enable`
   把文档恢复为可用状态。新禁用的文档会优先恢复到禁用前状态；历史老数据如果没有记录禁用前状态，则按“有 chunk 则恢复为 `INDEXED`，有错误则恢复为 `FAILED`，否则恢复为 `UPLOADED`”回退。

前端文档列表页、文档详情页和知识库概览页已经按照这套语义调整了文案和操作入口。

### Redis 缓存自愈与索引提交兜底

最近补了两类容易导致“看起来像随机故障”的防御逻辑：

1. `qa/readiness`、文档详情、chunk 列表等 Redis 缓存现在在读到不可反序列化的坏值时，会通过自定义 `CacheErrorHandler` 主动忽略坏缓存、回源重建并重写 Redis，而不是直接把 `SerializationException` 暴露成 500。
2. 异步索引提交阶段现在会对刚插入但短暂不可见的任务记录做短暂重试；如果 worker 在真正进入 `RUNNING` 之前就异常退出，也会显式把任务标记为 `FAILED`，避免长期停留在孤儿 `QUEUED` 状态。

### embedding 全量重嵌入

当前后端已提供：

1. `POST /api/admin/embeddings/rebuild`

该接口会在 embedding profile 发生变化后，异步提交一次全量重嵌入任务。前端知识库概览页已补入“重新嵌入向量”入口，并与 `qa/readiness` 的 `reembedRequired / reembedInProgress / currentRebuildRunId` 联动展示。

### 前端构建优化

当前前端已完成：

1. 路由级页面懒加载
2. `react-vendor / router / query / antd / antd-icons / vendor` 拆包

当前生产构建已不再出现最初的单一超大入口包问题。

### 系统健康观测

当前 `GET /api/health` 已返回结构化组件健康信息，不再只包含基础依赖存活状态：

1. `postgres`：执行 `SELECT 1` 验证数据库可用性
2. `redis`：执行 `PING` 验证 Redis 连通性
3. `embedding`：对当前配置的 embedding 接口发起最小真实请求，验证向量能力可用
4. `llm`：对当前配置的 chat completion 接口发起最小真实请求，验证回答能力可用

前端 `/health` 页面会展示各组件的状态、endpoint、provider/model、耗时和错误信息；`/api/health/redis-probe` 仍保留最小读写探针，用于确认 Redis 不只是能连通，也能正常读写。

## 运行方式

### 1. 启动依赖

```bash
docker compose up -d postgres redis
```

默认端口：

1. PostgreSQL：`5432`
2. Redis：`6379`
### 2. 启动应用

```bash
cd rag-backend
mvn spring-boot:run
```

如果使用 VSCode：

1. 打开 `rag-system.code-workspace`
2. 等待 Java 扩展完成 Maven 导入；根目录聚合 `pom.xml` 会帮助 Spring Boot Dashboard 识别 `rag-backend`
3. 使用 `.vscode/launch.json` 直接启动 Spring Boot
4. 或使用 `.vscode/tasks.json` 执行 `dev: full stack` 同时拉起依赖、后端和前端开发服务

### 2.1 启动方式建议

1. 后端启动后会自动把日志写入 `rag-backend/logs/rag-service.log`，并按日期/大小滚动归档到 `rag-backend/logs/archive/`。
2. 如果只想验证后端是否可用，可以直接检查：
   `curl --noproxy '*' -s http://127.0.0.1:8080/api/health`
3. 如果 Spring Boot Dashboard 没立刻显示应用，先执行一次 Maven Reload / Java: Clean Java Language Server Workspace，再重开 `rag-system.code-workspace`。

### 2.2 本地日志

默认本地日志策略：

1. 当前活跃日志文件：`rag-backend/logs/rag-service.log`
2. 历史归档目录：`rag-backend/logs/archive/`
3. 控制台日志仍然保留，便于开发时实时观察
4. 历史日志按日期和大小滚动切分，并自动压缩

常用查看命令：

```bash
cd rag-backend
tail -f logs/rag-service.log
```

### 3. 健康检查

```bash
curl --noproxy '*' -s http://127.0.0.1:8080/api/health
curl --noproxy '*' -s http://127.0.0.1:8080/api/health/redis-probe
```

## 关键配置

`rag-backend/src/main/resources/application.yml` 当前已经整理出这些主配置域：

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

1. embedding 模型：`text-embedding-v4`
2. 向量维度：`1024`
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
6. `DELETE /api/knowledge-bases/{kbCode}`

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

知识库删除说明：

1. `DELETE /api/knowledge-bases/{kbCode}` 会物理删除知识库
2. 会同时删除关联的 `document / document_chunk / indexing_task / chat_session / chat_message`
3. 会清理该知识库在本地上传目录下的物料，并同步失效相关 Redis 业务缓存
4. 如果知识库下仍有 `QUEUED / RUNNING` 的索引任务，请求会直接拒绝

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

这组评测基线当前已经有完整仓库资产支撑：

1. 评测口径说明：`docs/rfcs/RFC-0009-evaluation-dataset-and-acceptance-baseline.md`
2. 数据完整性测试：`QaEvaluationDatasetTest`
3. 真实检索评测夹具：`QaRetrievalEvaluationIntegrationTest`
4. 当前周记、状态文档和 RFC 已经能共同还原评测结论

### 2026-05-04 RAG 与 Redis 联调

知识库 `e2e-20260504` 已完成一次新的真实端到端联调：

1. 创建知识库 `e2e-20260504`
2. 上传文档 `DOC-309712409680023552`
3. `POST /process` 成功生成 `1` 个 chunk
4. `POST /embed` 成功写入 `1` 个向量
5. `GET /qa/readiness` 返回 `questionAnsweringReady=true`
6. `POST /qa/retrieve` 成功命中包含 Week 3 能力描述的真实 chunk
7. `POST /qa/ask` 返回答案：
   `根据检索内容，Week 3交付了异步索引、任务重试、过期任务恢复、结构化日志、外部化配置以及Redis业务缓存。`

本次联调同时验证了 Redis 业务缓存的真实写入：

1. 知识库详情缓存：`rag:knowledgeBaseDetail::e2e-20260504`
2. 知识库分页缓存：`rag:knowledgeBasePage::null:1:10`
3. `qa/readiness` 缓存：`rag:qaReadiness::e2e-20260504`
4. 检索结果缓存：`rag:qaRetrieval::e2e-20260504:What did Week 3 deliver?:3`

联调中的两个缓存现象已经确认原因：

1. `qaReadiness` 在最终快照里消失，是因为它的 TTL 配置本来就是 `60s`，在最后一轮观测时已经自然过期，不是写入失败。
2. `documentChunks` 键前后观测不一致，是因为联调时把“读接口请求”和“Redis 扫描”并行执行了，属于观测时序交叉，不是缓存未生效。

这次联调还顺手修正了 Redis 缓存序列化配置：

1. `RedisCacheConfig` 已注册 `JavaTimeModule`
2. `OffsetDateTime` 现在可以稳定写入和回读 Redis
3. 知识库详情、分页等命中缓存后的 JSON 返回已恢复正常

## 后续方向

下一阶段更适合继续推进这些事情：

1. 扩大评测集并沉淀稳定评分口径
2. 做无答案场景的召回抑制
3. 增加混合检索与重排序
4. 增加 session 复用与多轮对话
5. 补齐任务取消、批量编排和更完整观测能力

## 相关文档

1. [当前状态](/root/workspace/rag-system/docs/work/rag-backend/current-status.md)
2. [后端计划](/root/workspace/rag-system/docs/work/rag-backend/plan.md)
3. [Week 1](/root/workspace/rag-system/docs/work/rag-backend/week1.md)
4. [Week 2](/root/workspace/rag-system/docs/work/rag-backend/week2.md)
5. [Week 3](/root/workspace/rag-system/docs/work/rag-backend/week3.md)
6. [Week 4](/root/workspace/rag-system/docs/work/rag-backend/week4.md)
7. [前端当前状态](/root/workspace/rag-system/docs/work/rag-frontend/current-status.md)
8. [前端计划](/root/workspace/rag-system/docs/work/rag-frontend/plan.md)
9. [AI Gateway 当前状态](/root/workspace/rag-system/docs/work/rag-ai-service/current-status.md)
