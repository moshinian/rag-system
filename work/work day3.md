# Day 3：搭服务骨架

## 当前结论

Day 3 的核心目标已经基本完成，而且不是停留在“有配置文件”，而是已经做了真实验证。

当前已经落地并验证通过的内容：

1. Spring Boot 工程可启动
2. 包结构已按既定方向拆分
3. PostgreSQL 已真实接入
4. Flyway 迁移已真实执行
5. JPA 已真实落库
6. 基础配置文件已整理
7. 统一返回结构已完成
8. 全局异常处理已完成
9. 健康检查接口已完成
10. 知识库创建接口已完成
11. 文档上传占位接口已完成
12. 本地文件存储已完成

当前还没做的内容：

1. Redis 依赖与连通校验
2. Markdown / txt / PDF 解析
3. 文本切块
4. `document_chunk` 相关表与入库
5. 异步索引任务

所以 Day 3 现在的真实状态更准确地说是：

**服务骨架已经完成，且已经向 Day 4 的上传入口前进了一步。**

---

## 已完成项

### 1. 工程结构

当前实际结构：

```plaintext
rag-system/
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

实际验证结果：

1. 服务启动时 Hikari 成功连接 PostgreSQL
2. Flyway 成功执行 `V1__init_schema.sql`
3. 知识库创建接口写库成功
4. 文档上传接口写库成功

### 3. 配置管理

当前已完成：

1. `application.yml`
2. `application-local.yml`
3. `rag.storage.*`
4. `rag.embedding.*`
5. `rag.llm.*`

虽然 embedding 和 llm 还没接进业务链路，但配置入口已经预留。

### 4. common 层

已经完成：

1. 统一返回结构 `ApiResponse`
2. 错误码 `ErrorCode`
3. 业务异常 `BusinessException`
4. 全局异常处理 `GlobalExceptionHandler`

这部分已经满足 Day 3 对基础工程规范的要求。

### 5. controller 层

当前实际已有接口：

1. `GET /api/health`
2. `POST /api/knowledge-bases`
3. `POST /api/knowledge-bases/{kbCode}/documents/upload`

这里比最初 Day 3 计划多走了一步，因为知识库创建和文档上传已经不再是占位，而是已经可以真实调用。

### 6. service / repository / entity

当前已接通的业务链路：

1. `KnowledgeBaseService` -> `KnowledgeBaseRepository`
2. `DocumentService` -> `KnowledgeBaseRepository` + `DocumentRepository`
3. `LocalFileStorageService` -> 本地文件落盘

对应的实体与表：

1. `KnowledgeBaseEntity`
2. `DocumentEntity`
3. `knowledge_base`
4. `document`

---

## 实际验证记录

### 1. 知识库创建

已成功创建：

- `kbCode = settlement-kb`

接口返回成功，并写入 PostgreSQL。

### 2. 文档上传

已成功上传：

- `work/plan.md`

上传结果：

1. 文件成功落盘到 `data/uploads/...`
2. `document` 元数据成功写入 PostgreSQL
3. 状态初始化为 `UPLOADED`
4. 同知识库下重复上传会被拦截

---

## 仍未完成的 Day 3 项

严格按最初 Day 3 清单看，真正还没完成的只剩两类：

### 1. Redis

当前状态：

1. 只有配置占位
2. 还没有依赖接入
3. 还没有真实连通验证

这项仍未完成。

### 2. 工程化补充项

以下内容还没有开始：

1. 线程池配置
2. Swagger / OpenAPI
3. Redis 操作封装
4. 更完整的结构化日志

这些不影响当前主链路，但如果要把 Day 3 做成更完整的工程底座，还需要继续补。

---

## 为什么现在先不急着补 Redis

从当前项目阶段看，最缺的已经不是“再多一个基础设施”，而是：

1. 文档解析
2. 文本切块
3. `document_chunk` 数据建模
4. 上传后的后续处理链路

所以更合理的优先级是：

1. 先把解析和切块做出来
2. 再根据实际需要补 Redis

除非你明确想把 Day 3 的“基础设施清单”全部打勾，否则 Redis 不应该阻塞主线推进。

---

## 当前建议

如果继续按工程顺序推进，下一步建议是：

1. 补 `document_chunk` 表与实体
2. 先支持 `md` / `txt` 解析
3. 实现第一版固定长度切块
4. 保存 chunk 与元数据

如果继续按“阶段收口”推进，下一步建议是：

1. 补充 README 当前接口说明
2. 补充第 1 周进度文档
3. 再决定 Redis 是否单独补齐

---

## Day 3 最终判断

Day 3 已经不是“刚搭了个空架子”。

更准确的判断是：

**Day 3 已完成主体目标，并且已经提前完成了上传入口的一部分真实业务落地。**
