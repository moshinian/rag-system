# RAG Service

一个面向企业内部知识库场景的 RAG 后端服务，用来沉淀结算领域文档，并逐步演进为可检索、可引用、可追溯的问答系统。

当前仓库已经完成了第 1 周前半段的核心骨架，并且已经落地了两条真实链路：

1. 知识库创建
2. 文档上传入库

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
3. Flyway 迁移可执行
4. JPA 实体与 Repository 已接通
5. 统一响应结构 `ApiResponse`
6. 全局异常处理
7. 请求级 `X-Request-Id` 透传与生成
8. 健康检查接口 `/api/health`
9. 知识库创建接口
10. 文档上传接口
11. 本地文件落盘
12. 文档去重校验

当前还没完成的能力：

1. Redis 接入与连通校验
2. Markdown / txt / PDF 解析
3. `document_chunk` 表与切块入库
4. 异步索引任务
5. 向量化与检索
6. 大模型问答链路
7. 引用来源展示
8. 评测集与效果评测

## 技术选型

- Java 17
- Spring Boot 3.3.5
- Spring Web
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- Lombok

当前配置里已经预留但尚未真正接入主链路的外围能力：

- Redis 配置
- OpenAI 兼容 API 配置
- 检索与生成模块占位

## 目录结构

```text
rag-system/
├── pom.xml
├── src/main/java/com/example/rag/
│   ├── RagApplication.java
│   ├── common/                # 统一返回、错误码、异常
│   ├── config/                # 配置属性、请求 ID 过滤器
│   ├── controller/            # HTTP 接口
│   │   ├── HealthController.java
│   │   ├── KnowledgeBaseController.java
│   │   └── DocumentController.java
│   ├── generation/            # 生成链路占位
│   ├── ingestion/
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
│       └── V1__init_schema.sql
├── data/uploads/              # 已上传原始文件
└── work/                      # 过程文档与阶段记录
```

## 数据模型

当前 Flyway 初始化脚本已落地两张表：

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
- `storage_path`
- `file_size`
- `content_hash`
- `status`
- `version`
- `source`
- `tags`
- `error_message`

当前索引：

- `knowledge_base.kb_code` 唯一约束
- `document.document_code` 唯一约束
- `idx_document_kb_status`
- `idx_document_content_hash`

## 配置说明

主配置文件见 [src/main/resources/application.yml](/root/workspace/rag-system/src/main/resources/application.yml)。

当前数据库配置已经调整为：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: rag_user
    password: rag_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
  flyway:
    enabled: true
```

`application-local.yml` 不再排除数据源和 JPA 自动配置，默认 `local` profile 会真实连接 PostgreSQL。

文件存储目录：

```yaml
rag:
  storage:
    base-dir: ./data/uploads
```

## 当前接口

### 1. 健康检查

```http
GET /api/health
```

### 2. 创建知识库

```http
POST /api/knowledge-bases
Content-Type: application/json
```

请求示例：

```json
{
  "kbCode": "settlement-kb",
  "name": "Settlement Knowledge Base",
  "description": "Knowledge base for settlement documents",
  "createdBy": "codex"
}
```

### 3. 上传文档

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
3. Flyway 可成功执行迁移
4. 知识库创建接口可写库
5. 文档上传接口可写库
6. 原始文件可保存到本地目录
7. 同知识库重复文件会被拦截

示例上传落盘路径：

- [data/uploads/settlement-kb/20260428/DOC-1d10213fa0da4b84_plan.md](/root/workspace/rag-system/data/uploads/settlement-kb/20260428/DOC-1d10213fa0da4b84_plan.md)

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

服务会读取或生成 `X-Request-Id`，并在响应头中回写，便于问题排查。

实现见 [RequestIdFilter.java](/root/workspace/rag-system/src/main/java/com/example/rag/config/RequestIdFilter.java:1)。

### 异常处理

当前已区分：

1. 参数校验异常
2. 业务异常
3. 未知系统异常

实现见 [GlobalExceptionHandler.java](/root/workspace/rag-system/src/main/java/com/example/rag/common/exception/GlobalExceptionHandler.java:1)。

## 快速启动

### 1. 环境要求

- JDK 17
- Maven 3.9+
- PostgreSQL

### 2. 启动服务

```bash
mvn spring-boot:run
```

### 3. 验证服务

```bash
curl --noproxy '*' -s http://127.0.0.1:8080/api/health
```

## 下一步

当前最应该继续补的，不是再扩大量接口，而是把文档入库主链路往前推进一层：

1. 增加 `document_chunk` 表和实体
2. 支持 `md` / `txt` 解析
3. 实现第一版固定长度切块
4. 保存 chunk 与元数据
5. 再决定是否引入异步任务

Redis 建议放在这之后补。现在更缺的是文档解析和切块，不是缓存。

## 文档

过程文档统一放在 [work](/root/workspace/rag-system/work)：

- [work/preface.md](/root/workspace/rag-system/work/preface.md)
- [work/plan.md](/root/workspace/rag-system/work/plan.md)
- [work/week1.md](/root/workspace/rag-system/work/week1.md)
- [work/work day1.md](/root/workspace/rag-system/work/work%20day1.md)
- [work/work day2.md](/root/workspace/rag-system/work/work%20day2.md)
- [work/work day3.md](/root/workspace/rag-system/work/work%20day3.md)
