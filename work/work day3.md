# Day 3：搭服务骨架

## 当前结论

Day 3 的核心目标已经完成，而且不是停留在“有配置文件”，而是已经做了真实验证。

当前已经落地并验证通过的内容：

1. Spring Boot 工程可启动
2. 包结构已按既定方向拆分
3. PostgreSQL 已真实接入
4. Redis 已真实接入
5. Flyway 迁移已真实执行
6. JPA 已真实落库
7. 基础配置文件已整理
8. 统一返回结构已完成
9. 全局异常处理已完成
10. 健康检查接口已完成
11. Redis 探针接口已完成
12. 知识库创建接口已完成
13. 文档上传接口已完成
14. 本地文件存储已完成
15. 基础线程池已完成
16. Actuator 已接入

所以 Day 3 现在的真实状态更准确地说是：

**服务骨架已经完成，基础设施底座已收口，并且已经提前进入 Day 4 的上传入口范围。**

## 已完成项

### 1. 工程结构

当前实际结构：

```text
rag-system/
├── docker-compose.yml
├── pom.xml
├── src/main/java/com/example/rag/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── model/
│   ├── ingestion/
│   ├── retrieval/
│   ├── generation/
│   ├── integration/
│   ├── config/
│   └── common/
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── db/migration/
└── work/
```

这套结构和最初规划已经基本一致，后续再往里补解析、切块、检索，不需要再重构目录。

### 2. PostgreSQL

已完成内容：

1. 数据源配置完成
2. 本地 `local` profile 已开启真实数据库链路
3. Flyway 依赖补齐
4. Flyway PostgreSQL 模块补齐
5. 数据库迁移已真实执行
6. JPA 实体已能正常落库
7. PostgreSQL 容器重建后，迁移可自动恢复表结构

实际验证结果：

1. 服务启动时 Hikari 成功连接 PostgreSQL
2. Flyway 成功执行 `V1__init_schema.sql`
3. 新建空库后，Flyway 仍可恢复表结构
4. 知识库创建接口写库成功
5. 文档上传接口写库成功

### 3. Redis

已完成内容：

1. Redis 依赖已接入
2. Redis 配置已补齐
3. Redis 密码已与本地容器对齐
4. `StringRedisTemplate` 已可用
5. 最小 `set/get` 探针已落地

实际验证结果：

1. 服务启动可连上 Redis
2. Redis 探针接口可完成一次最小读写

### 4. 配置管理

当前已完成：

1. `application.yml`
2. `application-local.yml`
3. `rag.storage.*`
4. `rag.embedding.*`
5. `rag.llm.*`
6. `docker-compose.yml`

虽然 embedding 和 llm 还没接进业务链路，但配置入口已经预留。

### 5. common 层

已经完成：

1. 统一返回结构 `ApiResponse`
2. 错误码 `ErrorCode`
3. 业务异常 `BusinessException`
4. 全局异常处理 `GlobalExceptionHandler`

### 6. controller 层

当前实际已有接口：

1. `GET /api/health`
2. `POST /api/health/redis-probe`
3. `POST /api/knowledge-bases`
4. `POST /api/knowledge-bases/{kbCode}/documents/upload`

### 7. 线程池

当前已新增：

1. `indexingExecutor`

它的意义不是立刻做异步任务，而是为后续文档解析、切块、索引任务预留执行器。

## 实际验证记录

### 1. 容器与基础设施

已确认可运行：

1. `rag-postgres`
2. `rag-redis`

并且都已开启本地持久化目录。

### 2. 知识库创建

已成功创建：

- `kbCode = settlement-kb`

接口返回成功，并写入 PostgreSQL。

### 3. 文档上传

已成功上传：

- `work/plan.md`

上传结果：

1. 文件成功落盘到 `data/uploads/...`
2. `document` 元数据成功写入 PostgreSQL
3. 状态初始化为 `UPLOADED`
4. 同知识库下重复上传会被拦截

## 仍未完成的 Day 3 项

严格按最初 Day 3 清单看，真正还没完成的只剩加分项：

1. Swagger / OpenAPI
2. 更完整的结构化日志
3. 更系统化的 Redis 封装

这些不影响当前主链路，所以不应该继续阻塞 Day 4 / Day 5。

## 当前建议

如果继续按主线推进，下一步建议是：

1. 补 `document_chunk` 表与实体
2. 先支持 `md` / `txt` 解析
3. 实现第一版固定长度切块
4. 保存 chunk 与元数据

## Day 3 最终判断

**Day 3 主体已完成，验收通过。**
