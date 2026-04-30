# RAG Service

一个面向企业内部知识库场景的 RAG 后端服务，用来沉淀结算领域文档，并逐步演进为可检索、可引用、可追溯的问答系统。

当前仓库已经完成了第 1 周前半段到 Day 3 的核心工程骨架，并且已经落地了两条真实链路：

1. 知识库创建
2. 文档上传入库

同时已经补齐了本地开发所需的基础设施底座：

1. PostgreSQL 容器化运行与持久化
2. Redis 容器化运行与持久化
3. Flyway 迁移恢复能力
4. 线程池基础配置
5. Redis 最小读写验证接口

当前 Day 4 的上传与存储链路也已经完成收口，已经可以直接作为 Day 5 解析与切块的前置。

这份 README 只描述当前仓库已经实现的内容，以及下一步明确要做的事情，不把规划写成现状。

## 项目目标

这个项目面向产品、开发、测试、运维等内部团队，解决以下问题：

1. 业务文档分散，检索成本高
2. 关键知识依赖资深同事经验，难沉淀
3. 排查问题时难以快速定位相关设计文档和操作手册
4. 新成员熟悉业务周期长

项目的目标不是做泛化聊天机器人，而是先把企业知识库 RAG 的主链路做完整：

```text
知识库创建 -> 文档上传 -> 原始文件存储 -> 元数据入库 -> 文档解析 -> 文本切块 -> 检索 -> 回答 -> 引用来源展示
```

## 当前已实现

当前仓库已经落地的能力：

1. Spring Boot 3 + Java 17 服务骨架
2. PostgreSQL 真连通
3. Redis 真连通
4. Flyway 迁移可执行
5. JPA 实体与 Repository 已接通
6. 统一响应结构 `ApiResponse`
7. 全局异常处理
8. 请求级 `X-Request-Id` 透传与生成
9. 健康检查接口 `/api/health`
10. Redis 探针接口 `/api/health/redis-probe`
11. 知识库创建接口
12. 文档上传接口
13. 本地文件落盘
14. 文档去重校验
15. 文档状态枚举已预留到 `INDEXED / FAILED / DISABLED`
16. 文档 `media_type` 元数据已落库
17. Day 4 样本文档已补齐 `md / txt / pdf`
18. `document_chunk` 表、实体与 Repository 已落地
19. `md / txt` 第一版解析已落地
20. 第一版固定长度切块已落地
21. 文档处理接口 `/process` 已接入
22. 基础线程池 `indexingExecutor`
23. Actuator 基础接入

当前还没完成的能力：

1. PDF 解析
2. 异步索引任务编排
3. 向量化与检索
4. 大模型问答链路
5. 引用来源展示
6. 评测集与效果评测

## 技术选型

- Java 17
- Spring Boot 3.5.14
- Spring Web
- Spring Validation
- Spring Data JPA
- Spring Data Redis
- Spring Boot Actuator
- PostgreSQL
- Redis
- Flyway
- Lombok

当前配置里已经预留但尚未真正接入主链路的外围能力：

- `rag.embedding.*`
- `rag.llm.*`
- 检索与生成模块占位

## 目录结构

```text
rag-system/
├── docker-compose.yml
├── pom.xml
├── src/main/java/com/example/rag/
│   ├── RagApplication.java
│   ├── common/                # 统一返回、错误码、异常
│   ├── config/                # 配置属性、线程池、请求 ID 过滤器
│   ├── controller/            # HTTP 接口
│   │   ├── HealthController.java
│   │   ├── KnowledgeBaseController.java
│   │   └── DocumentController.java
│   ├── generation/            # 生成链路占位
│   ├── ingestion/
│   │   ├── chunk/             # 文本切块
│   │   ├── parser/            # 文档解析
│   │   └── storage/           # 本地文件存储
│   ├── integration/
│   │   └── llm/               # OpenAI 兼容客户端占位
│   ├── model/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── enums/
│   │   ├── request/
│   │   └── response/
│   ├── repository/
│   ├── retrieval/             # 检索链路占位
│   └── service/
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── db/migration/
│       ├── V1__init_schema.sql
│       ├── V2__drop_serial_defaults.sql
│       ├── V3__add_document_media_type.sql
│       └── V4__create_document_chunk_table.sql
├── data/                      # 本地持久化目录（已被 .gitignore 忽略）
└── work/                      # 过程文档与阶段记录
```

## 数据模型

当前 Flyway 脚本已落地三张表：

### `knowledge_base`

用于管理知识库本身。

核心字段：

- `kb_code`
- `name`
- `description`
- `status`
- `created_by`
- `created_at`
- `updated_at`

### `document`

用于保存原始文档元数据。

核心字段：

- `knowledge_base_id`
- `document_code`
- `file_name`
- `display_name`
- `file_type`
- `media_type`
- `storage_path`
- `file_size`
- `content_hash`
- `status`
- `version`
- `source`
- `tags`
- `error_message`

### `document_chunk`

用于保存解析和切块后的检索基础数据。

核心字段：

- `knowledge_base_id`
- `document_id`
- `chunk_index`
- `chunk_type`
- `title`
- `content`
- `content_length`
- `token_count`
- `start_offset`
- `end_offset`
- `metadata_json`
- `status`

当前索引：

- `knowledge_base.kb_code` 唯一约束
- `document.document_code` 唯一约束
- `idx_document_kb_status`
- `idx_document_content_hash`
- `uk_document_chunk_document_index`
- `idx_document_chunk_kb_document`
- `idx_document_chunk_document_status`

当前文档状态枚举：

- `UPLOADED`
- `PARSING`
- `PARSED`
- `CHUNKING`
- `INDEXED`
- `FAILED`
- `DISABLED`

## 本地运行

### 1. 启动基础设施

项目根目录已经提供 [docker-compose.yml](/root/workspace/rag-system/docker-compose.yml:1)：

```bash
docker compose up -d
```

当前会启动：

1. `rag-postgres`
2. `rag-redis`

其中：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- Redis 已启用密码：`rag_password`
- 数据持久化目录：`./data/postgres`、`./data/redis`

### 2. 启动应用

```bash
mvn -s maven-settings.xml spring-boot:run
```

或者执行测试做一次完整启动校验：

```bash
mvn -s maven-settings.xml test
```

## 配置说明

主配置文件见 [src/main/resources/application.yml](/root/workspace/rag-system/src/main/resources/application.yml:1)。

当前关键配置包括：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: rag_user
    password: rag_password
  data:
    redis:
      host: localhost
      port: 6379
      password: rag_password
  flyway:
    enabled: true

rag:
  storage:
    base-dir: ./data/uploads
```

`application-local.yml` 负责本地环境增强配置，包括更细的日志级别。

## 当前接口

### 1. 健康检查

```http
GET /api/health
```

当前返回除了服务状态，还会附带组件状态：

1. `postgres`
2. `redis`

### 2. Redis 连通探针

```http
POST /api/health/redis-probe
```

这个接口会执行一次最小 `set/get`，返回：

1. 写入 key
2. 写入值
3. 读出值
4. 是否一致

### 3. 创建知识库

```http
POST /api/knowledge-bases
Content-Type: application/json
```

### 4. 上传文档

```http
POST /api/knowledge-bases/{kbCode}/documents/upload
Content-Type: multipart/form-data
```

### 5. 处理文档

```http
POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/process
```

这个接口负责：

1. 根据文件类型选择解析器
2. 执行第一版切块
3. 写入 `document_chunk`
4. 推进文档状态到 `INDEXED` 或 `FAILED`

请求示例：

```json
{
  "kbCode": "settlement-kb",
  "name": "Settlement Knowledge Base",
  "description": "Knowledge base for settlement documents",
  "createdBy": "codex"
}
```

### 4. 上传文档

```http
POST /api/knowledge-bases/{kbCode}/documents/upload
Content-Type: multipart/form-data
```

表单字段：

- `file`
- `documentName`，可选
- `tags`，可选
- `source`，可选
- `operator`，可选

支持文件类型：

- `md`
- `txt`
- `pdf`

## 已验证结果

当前仓库已经做过实际验证：

1. Spring Boot 可正常启动
2. PostgreSQL 可正常连接
3. Redis 可正常连接
4. Flyway 可成功执行迁移
5. 新建 PostgreSQL 容器后可重新建表
6. 知识库创建接口可写库
7. 文档上传接口可写库
8. 原始文件可保存到本地目录
9. 同知识库重复文件会被拦截
10. Redis 探针接口可完成一次最小读写

## 已实现的工程约束

### 统一返回结构

所有接口统一返回：

- `code`
- `message`
- `data`
- `requestId`
- `timestamp`

实现见 [ApiResponse.java](/root/workspace/rag-system/src/main/java/com/example/rag/common/ApiResponse.java:1)。

### 请求追踪

当前已经接入请求级 `X-Request-Id` 透传与生成，便于后续日志排障和接口追踪。

请求 ID 基于 Snowflake 算法生成；当系统时钟出现短暂回拨，或者同一毫秒内序列号耗尽时，生成器会阻塞等待到下一可用毫秒，而不是直接抛错。

### 基础线程池

当前已经提供 `indexingExecutor`，为后续异步解析、切块、索引任务预留执行器。

实现见 [ExecutorConfig.java](/root/workspace/rag-system/src/main/java/com/example/rag/config/ExecutorConfig.java:1)。

## 下一步

当前 Day 3 已基本收口，下一阶段应该直接进入 Day 4 / Day 5 主线：

1. 完成 Day 4 的文档上传链路复核与样本文档整理
2. 新增 `document_chunk` 表、实体、Repository
3. 实现 Markdown / txt 第一版解析
4. 实现固定长度切块策略
5. 保存 chunk 与元数据

更详细的阶段记录可参考：

1. [work/current-status.md](/root/workspace/rag-system/work/current-status.md)
2. [work/week1.md](/root/workspace/rag-system/work/week1.md)
3. [work/work day3.md](/root/workspace/rag-system/work/work%20day3.md)
