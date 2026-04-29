# 当前状态

## 当前结论

当前工程已经完成了第 1 周前半段最关键的骨架工作，并且已经完成 Day 3 收口：

1. 知识库创建链路可运行
2. 文档上传入库链路可运行
3. PostgreSQL 已真实连通并支持重建后重新迁移
4. Redis 已真实连通并可完成最小读写验证
5. 线程池和 Actuator 基础底座已补齐

这意味着项目已经从“有后端服务、有数据库落地、有文件存储入口”进一步进入了“基础设施底座齐备，可以开始做 Day 4 / Day 5 主链路”的阶段。

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
2. `application.yml` / `application-local.yml` 已真实走数据库链路
3. Flyway 已接入
4. Flyway PostgreSQL 模块已补齐
5. `V1__init_schema.sql` 已成功执行
6. PostgreSQL 容器重建后，迁移可自动恢复表结构
7. JPA 实体与 Repository 已打通

### Redis

1. Redis 依赖已接入
2. Redis 配置已补齐
3. Redis 已配置密码并与本地容器对齐
4. `StringRedisTemplate` 已接入
5. 最小 `set/get` 验证能力已落地

### 数据模型

当前已落地：

1. `knowledge_base`
2. `document`

当前已有实体：

1. `KnowledgeBaseEntity`
2. `DocumentEntity`

### 已有接口

1. `GET /api/health`
2. `POST /api/health/redis-probe`
3. `POST /api/knowledge-bases`
4. `POST /api/knowledge-bases/{kbCode}/documents/upload`

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

## 已验证

已经做过实际验证的内容：

1. Spring Boot 服务可启动
2. PostgreSQL 连接成功
3. Redis 连接成功
4. Flyway 迁移成功
5. 新 PostgreSQL 容器可自动恢复表结构
6. 知识库创建接口写库成功
7. 文档上传接口写库成功
8. 上传文件已实际保存到本地目录
9. 重复上传会返回业务错误
10. Redis 探针接口可读写成功

当前已验证样例：

1. 知识库：`settlement-kb`
2. 文档：`work/plan.md`

## 当前未完成

### 文档处理

1. Markdown 解析未开始
2. txt 解析未开始
3. PDF 解析未开始
4. 文本切块未开始
5. `document_chunk` 表未落地
6. chunk 入库未开始

### 检索与问答

1. embedding 业务链路未开始
2. 向量存储未开始
3. 检索链路未开始
4. 大模型问答未开始
5. 引用来源展示未开始

### 工程化补充

1. 异步索引任务编排未开始
2. OpenAPI / Swagger 未开始
3. 更完整的结构化日志未开始
4. 评测集未开始

## 当前判断

如果只看主线推进，当前最缺的已经不是继续补基础设施，而是开始真正做文档处理主链路。

所以当前阶段的真实优先级应该是：

1. `document_chunk` 数据建模
2. Markdown / txt 解析
3. 第一版切块策略
4. chunk 入库
5. 之后再进入检索和问答

## 下一步建议

建议按下面顺序继续：

1. 进入 Day 4，复核上传链路、样本文档和文档状态设计
2. 增加 `document_chunk` 表、实体、Repository
3. 先实现 `md` / `txt` 解析
4. 做第一版固定长度切块
5. 保存 chunk 与元数据

## 备注

这份文档只负责记录“当前真实状态”，不重复写长计划。

更详细的背景和过程可参考：

1. [README.md](/root/workspace/rag-system/README.md)
2. [work/week1.md](/root/workspace/rag-system/work/week1.md)
3. [work/work day3.md](/root/workspace/rag-system/work/work%20day3.md)
