# 当前状态

## 当前结论

当前工程已经完成了第 1 周最关键的骨架和文档处理主链路首版：

1. 知识库创建链路可运行
2. 文档上传入库链路可运行
3. PostgreSQL 已真实连通并支持重建后重新迁移
4. Redis 已真实连通并可完成最小读写验证
5. 线程池和 Actuator 基础底座已补齐

这意味着项目已经从“有后端服务、有数据库落地、有文件存储入口”进一步进入了“Day 5 主链路已开始落地，已经具备第一版解析与切块能力”的阶段。

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
3. `document_chunk`

当前已有实体：

1. `KnowledgeBaseEntity`
2. `DocumentEntity`
3. `DocumentChunkEntity`

### 已有接口

1. `GET /api/health`
2. `POST /api/health/redis-probe`
3. `POST /api/knowledge-bases`
4. `POST /api/knowledge-bases/{kbCode}/documents/upload`
5. `POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/process`

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
9. `media_type` 元数据已落库
10. 文档状态枚举已预留到 `INDEXED / FAILED / DISABLED`

### 文档处理链路

已完成能力：

1. `document_chunk` 表已落地
2. `md` 第一版解析已完成
3. `txt` 第一版解析已完成
4. 第一版固定长度切块已完成
5. chunk 元数据写库能力已完成
6. 文档状态流转已开始落地
7. 文档处理接口已接入控制层

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
11. `media_type` 回填迁移脚本已补入仓库
12. `document_chunk` 迁移脚本已补入仓库
13. 文档处理服务单元测试已通过
14. 文档处理真实写库集成测试已通过

当前已验证样例：

1. 知识库：`settlement-kb`
2. 文档：`work/plan.md`
3. 样例：`work/samples/day4-upload-sample.md`
4. 样例：`work/samples/day4-upload-sample.txt`
5. 样例：`work/samples/day4-upload-sample.pdf`

## 当前未完成

### 文档处理

1. PDF 解析未开始
2. 更细的 Markdown 结构化解析未开始
3. 更智能的切块策略未开始

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

如果只看主线推进，当前最缺的已经不是继续补基础设施，也不是从零开始做解析切块，而是进入检索前的最后缺口补齐。

所以当前阶段的真实优先级应该是：

1. PDF 解析
2. embedding 业务链路
3. 向量存储与检索
4. 问答链路
5. 之后再进入评测和优化

## 下一步建议

建议按下面顺序继续：

1. 继续 Day 5 / Day 6，补 PDF 解析
2. 开始 embedding 与向量存储设计
3. 接入基础检索链路
4. 准备第一版问答接口
5. 再进入评测与优化

## 备注

这份文档只负责记录“当前真实状态”，不重复写长计划。

更详细的背景和过程可参考：

1. [README.md](/root/workspace/rag-system/README.md)
2. [work/week1.md](/root/workspace/rag-system/work/week1.md)
3. [work/work day3.md](/root/workspace/rag-system/work/work%20day3.md)
