# 当前状态

## 当前结论

当前工程已经完成了第 1 周前半段最关键的骨架工作，并且已经有两条真实可运行的业务链路：

1. 知识库创建
2. 文档上传入库

这意味着项目已经从“只有设计和规划”进入了“有后端服务、有数据库落地、有文件存储入口”的阶段。

---

## 已完成

### 工程基础

1. Spring Boot 3 + Java 17 工程已搭好
2. 包结构已按 `controller / service / repository / model / ingestion / integration / common / config` 拆分
3. 统一响应结构已完成
4. 全局异常处理已完成
5. 请求级 `X-Request-Id` 已接入
6. 健康检查接口已完成

### PostgreSQL

1. PostgreSQL 已真实连通
2. `application.yml` / `application-local.yml` 已调整为真实走数据库链路
3. Flyway 已接入
4. Flyway PostgreSQL 模块已补齐
5. `V1__init_schema.sql` 已成功执行
6. JPA 实体与 Repository 已打通

### 数据模型

当前已落地：

1. `knowledge_base`
2. `document`

当前已有实体：

1. `KnowledgeBaseEntity`
2. `DocumentEntity`

### 已有接口

1. `GET /api/health`
2. `POST /api/knowledge-bases`
3. `POST /api/knowledge-bases/{kbCode}/documents/upload`

### 文档上传链路

已完成能力：

1. 知识库存在性校验
2. 文件类型校验
3. 空文件校验
4. `SHA-256` 内容摘要计算
5. 同知识库重复文件拦截
6. 原始文件本地落盘
7. `document` 元数据写入 PostgreSQL
8. 初始状态写为 `UPLOADED`

---

## 已验证

已经做过实际验证的内容：

1. Spring Boot 服务可启动
2. PostgreSQL 连接成功
3. Flyway 迁移成功
4. 知识库创建接口写库成功
5. 文档上传接口写库成功
6. 上传文件已实际保存到本地目录
7. 重复上传会返回业务错误

当前已验证样例：

1. 知识库：`settlement-kb`
2. 文档：`work/plan.md`

---

## 当前未完成

### 基础设施

1. Redis 依赖未接入
2. Redis 连通校验未完成
3. Redis 操作封装未开始

### 文档处理

1. Markdown 解析未开始
2. txt 解析未开始
3. PDF 解析未开始
4. 文本切块未开始
5. `document_chunk` 表未落地
6. chunk 入库未开始

### 检索与问答

1. embedding 接入未开始
2. 向量存储未开始
3. 检索链路未开始
4. 大模型问答未开始
5. 引用来源展示未开始

### 工程化补充

1. 异步索引任务未开始
2. 线程池配置未开始
3. OpenAPI / Swagger 未开始
4. 更完整的结构化日志未开始
5. 评测集未开始

---

## 当前判断

如果只看主线推进，当前最缺的已经不是再补一个基础设施，而是把文档处理主链路继续往前推进。

所以当前阶段的真实优先级应该是：

1. `document_chunk` 数据建模
2. Markdown / txt 解析
3. 第一版切块策略
4. chunk 入库
5. 之后再看 Redis 是否需要补齐

---

## 下一步建议

建议按下面顺序继续：

1. 增加 `document_chunk` 表、实体、Repository
2. 先实现 `md` / `txt` 解析
3. 做第一版固定长度切块
4. 保存 chunk 与元数据
5. 再决定是否把 Redis 作为 Day 3 收尾项补上

---

## 备注

这份文档只负责记录“当前真实状态”，不重复写长计划。

更详细的背景和过程可参考：

1. [README.md](/root/workspace/rag-system/README.md)
2. [work/week1.md](/root/workspace/rag-system/work/week1.md)
3. [work/work day3.md](/root/workspace/rag-system/work/work%20day3.md)
