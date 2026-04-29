# Day 2：定数据模型

## 今日目标

今天的核心任务不是写很多代码，而是把系统的数据结构设计清楚。

你要解决的问题是：

1. 系统里有哪些核心对象
2. 这些对象之间是什么关系
3. 文档从上传到切块完成，中间状态怎么流转
4. 后续问答、索引任务、文档更新要怎么留扩展口

如果 Day 2 做扎实，后面写接口、落表、做异步任务时才不会反复推翻。

---

## 核心设计原则

当前阶段的数据模型设计遵循 4 个原则：

1. 先保证主链路完整，再考虑复杂扩展
2. 主数据尽量统一放在 PostgreSQL
3. 状态要可追踪，便于排障和讲解
4. 字段设计要为后续向量检索、问答记录和文档更新预留空间

---

## 核心表设计

第一阶段建议先落这 6 张核心表：

1. `knowledge_base`
2. `document`
3. `document_chunk`
4. `indexing_task`
5. `chat_session`
6. `chat_message`

---

## 1. knowledge_base

知识库表用于管理一组业务上相关的文档集合。

建议核心字段：

1. `id`
2. `kb_code`
3. `name`
4. `description`
5. `status`
6. `created_by`
7. `created_at`
8. `updated_at`

字段说明：

1. `id`：主键，建议使用 `bigserial` 或雪花 ID
2. `kb_code`：知识库唯一编码，便于接口层和外部系统使用
3. `name`：知识库名称
4. `description`：知识库说明
5. `status`：知识库状态，例如启用、停用
6. `created_by`：创建人
7. `created_at`：创建时间
8. `updated_at`：更新时间

当前阶段作用：

1. 给文档分组
2. 为后续检索限定范围
3. 支撑多知识库扩展

---

## 2. document

文档表用于保存原始文档的元数据，是文档上传和解析链路的核心主表。

建议核心字段：

1. `id`
2. `knowledge_base_id`
3. `document_code`
4. `file_name`
5. `file_type`
6. `storage_path`
7. `file_size`
8. `content_hash`
9. `status`
10. `version`
11. `error_message`
12. `created_by`
13. `created_at`
14. `updated_at`

字段说明：

1. `knowledge_base_id`：所属知识库
2. `document_code`：文档唯一编码
3. `file_name`：原始文件名
4. `file_type`：例如 `md`、`txt`、`pdf`
5. `storage_path`：原始文件存储路径
6. `file_size`：文件大小
7. `content_hash`：文档内容摘要，用于防重和变更识别
8. `status`：文档处理状态
9. `version`：文档版本号，为更新场景预留
10. `error_message`：失败原因，便于排障

建议文档状态：

1. `UPLOADED`
2. `PARSING`
3. `PARSED`
4. `CHUNKING`
5. `INDEXED`
6. `FAILED`
7. `DISABLED`

这样设计的原因：

1. 可以清楚表示文档处理进度
2. 可以区分“上传成功”和“入库完成”
3. 后续接异步任务、重试机制时不需要重构主表

---

## 3. document_chunk

`document_chunk` 是 RAG 系统里最关键的检索基础数据表，后续向量检索和引用来源都依赖它。

建议核心字段：

1. `id`
2. `knowledge_base_id`
3. `document_id`
4. `chunk_index`
5. `chunk_type`
6. `title`
7. `content`
8. `content_length`
9. `token_count`
10. `start_offset`
11. `end_offset`
12. `metadata_json`
13. `embedding`
14. `status`
15. `created_at`
16. `updated_at`

字段说明：

1. `knowledge_base_id`：冗余保存，便于按知识库直接过滤
2. `document_id`：所属文档
3. `chunk_index`：文档内块序号
4. `chunk_type`：例如正文、标题、表格解析结果
5. `title`：当前 chunk 对应标题或章节名
6. `content`：chunk 文本内容
7. `content_length`：字符长度
8. `token_count`：预估 token 数，便于后续调参
9. `start_offset`：原文起始位置
10. `end_offset`：原文结束位置
11. `metadata_json`：扩展元数据，例如页码、段落号、标题层级
12. `embedding`：向量字段，使用 `pgvector`
13. `status`：chunk 状态，例如有效、失效

为什么这张表要设计得稍微完整一些：

1. 后续要支持引用来源展示
2. 后续要支持切块策略对比
3. 后续要支持文档更新后的失效处理
4. 面试时可以体现你不是只存一段文本，而是在做可追溯的检索数据建模

---

## 4. indexing_task

`indexing_task` 用于记录一次文档索引任务的执行情况，是后续异步化和失败重试的基础。

建议核心字段：

1. `id`
2. `task_code`
3. `knowledge_base_id`
4. `document_id`
5. `task_type`
6. `status`
7. `retry_count`
8. `trigger_source`
9. `started_at`
10. `finished_at`
11. `error_message`
12. `created_at`
13. `updated_at`

字段说明：

1. `task_code`：任务唯一编码
2. `task_type`：例如解析、切块、向量化
3. `status`：任务状态
4. `retry_count`：已重试次数
5. `trigger_source`：人工触发、上传触发、重试触发
6. `started_at` / `finished_at`：执行时间记录
7. `error_message`：失败详情

建议任务状态：

1. `PENDING`
2. `RUNNING`
3. `SUCCESS`
4. `FAILED`
5. `CANCELED`

这张表的价值：

1. 为异步任务和重试机制留口
2. 可以支撑任务追踪页面
3. 面试时能体现工程化思维，而不是只做一个同步 demo

---

## 5. chat_session

虽然第 1 周不重点做问答，但表结构可以先预留，避免后面补链路时推翻设计。

建议核心字段：

1. `id`
2. `session_code`
3. `knowledge_base_id`
4. `session_name`
5. `created_by`
6. `created_at`
7. `updated_at`

作用：

1. 组织一组对话
2. 支撑问答历史查询
3. 后续支持多轮会话上下文

---

## 6. chat_message

`chat_message` 用于记录具体的一轮问题和回答。

建议核心字段：

1. `id`
2. `session_id`
3. `message_type`
4. `question`
5. `answer`
6. `retrieved_chunks`
7. `prompt_template`
8. `model_name`
9. `latency_ms`
10. `created_at`

字段说明：

1. `message_type`：用户提问或系统回答
2. `question`：用户问题
3. `answer`：模型答案
4. `retrieved_chunks`：召回片段信息，可先用 `jsonb`
5. `prompt_template`：记录当时使用的模板版本
6. `model_name`：调用模型名称
7. `latency_ms`：响应耗时

为什么现在就预留：

1. 后续接问答接口时会更顺
2. 便于后面做效果复盘
3. 面试时可以讲“我从一开始就在为可观测性和评测做准备”

---

## 表关系设计

当前阶段的关系可以理解为：

1. 一个 `knowledge_base` 下有多个 `document`
2. 一个 `document` 下有多个 `document_chunk`
3. 一个 `document` 可以关联多条 `indexing_task`
4. 一个 `knowledge_base` 下可以有多个 `chat_session`
5. 一个 `chat_session` 下可以有多条 `chat_message`

可以先用下面这个关系去理解：

```plaintext
knowledge_base
  └── document
        ├── document_chunk
        └── indexing_task

knowledge_base
  └── chat_session
        └── chat_message
```

---

## 索引建议

虽然今天重点不是写 SQL，但必须先知道哪些字段以后要建索引。

建议优先考虑：

1. `knowledge_base.kb_code` 唯一索引
2. `document.document_code` 唯一索引
3. `document(knowledge_base_id, status)` 普通索引
4. `document(content_hash)` 普通索引
5. `document_chunk(document_id, chunk_index)` 唯一索引
6. `document_chunk(knowledge_base_id)` 普通索引
7. `indexing_task(task_code)` 唯一索引
8. `indexing_task(document_id, status)` 普通索引
9. `chat_session(session_code)` 唯一索引
10. `chat_message(session_id, created_at)` 普通索引

后续向量检索接入后，再补：

1. `document_chunk.embedding` 的向量索引

---

## 状态流转设计

Day 2 最需要讲清楚的不是“表长什么样”，而是状态怎么动。

### 文档状态流转

建议第一版：

```plaintext
UPLOADED -> PARSING -> PARSED -> CHUNKING -> INDEXED
                           \-> FAILED
                 \-> FAILED
```

解释：

1. 文档上传成功后进入 `UPLOADED`
2. 开始解析时进入 `PARSING`
3. 解析完成后进入 `PARSED`
4. 开始切块和入库时进入 `CHUNKING`
5. chunk 写入完成后进入 `INDEXED`
6. 任一阶段异常都可以进入 `FAILED`

### 任务状态流转

```plaintext
PENDING -> RUNNING -> SUCCESS
                   \-> FAILED
```

如果后续需要人工取消，再加：

```plaintext
RUNNING -> CANCELED
```

---

## 需要提前想清楚的边界问题

### 1. 文档重复上传怎么处理

第一阶段建议基于 `content_hash` 做基础防重。

可选策略：

1. 同知识库内完全相同内容，直接拒绝上传
2. 同知识库内完全相同内容，提示已存在
3. 先允许上传，但标记重复文档

当前更推荐：

1. 默认提示重复，不重复建索引

原因：

1. 逻辑简单
2. 更适合演示和讲解
3. 避免重复 chunk 污染检索结果

### 2. 文档更新怎么处理

第一阶段不用做复杂版本树，但要预留字段。

建议：

1. `document.version` 记录版本号
2. 新版本文档重新上传时，新建记录或覆盖旧记录都可以
3. 如果覆盖旧记录，要把旧 chunk 失效

当前更适合的讲法：

1. 第一阶段采用“重新入库 + 旧 chunk 失效”的简单策略

### 3. 解析失败怎么记录

建议同时记录两层状态：

1. `document.status = FAILED`
2. `indexing_task.error_message` 保存具体错误

这样可以区分：

1. 文档整体处理失败
2. 某次任务为什么失败

---

## Day 2 产出物

今天完成后，至少应该有这些东西：

1. 核心表设计文档
2. 表关系说明
3. 文档状态流转说明
4. 任务状态流转说明
5. 字段索引建议
6. 文档重复上传和更新处理策略初版

---

## 面试可用 Q&A

### 1. 为什么一开始就设计 `indexing_task`

因为文档处理天然是一个任务型流程，后续大概率要异步执行，还会涉及重试、失败追踪和任务排队。如果一开始不把任务模型抽出来，后面从同步 demo 演进到可维护系统时会有明显重构成本。

### 2. 为什么 `document_chunk` 要保留那么多元数据

因为 RAG 的关键不是把文本切开存起来，而是让后续检索结果可引用、可定位、可复盘。保留标题、位置、页码、chunk 序号这些信息，后面展示引用来源、做效果分析、调整切块策略都会更容易。

### 3. 为什么主数据统一放 PostgreSQL

第一阶段的重点是尽快完成端到端闭环，而不是引入过多基础设施。把知识库、文档、chunk、任务和问答记录统一放在 PostgreSQL，可以降低系统复杂度，也更便于做事务一致性、状态管理和后续排障。

### 4. 为什么文档状态和任务状态要分开

因为它们表达的层次不同。文档状态表示一份文档当前所处的业务生命周期，而任务状态表示某一次处理动作的执行结果。把这两层拆开，既能看业务视角，也能看执行视角，问题定位会更清晰。

### 5. 这一版数据模型最重要的设计价值是什么

最重要的价值是：它不是只为“今天能跑”服务，而是已经为后续的异步索引、向量检索、问答记录、失败重试和文档更新预留了扩展空间。这样项目在演进过程中不会频繁推翻底层数据结构。

---

## 今日结论

Day 2 的目标不是把所有字段设计到完美，而是先建立一个足够稳定、足够可扩展、足够好讲的数据骨架。

今天结束后，你应该能清楚回答：

1. 系统的核心对象有哪些
2. 它们之间如何关联
3. 文档状态如何流转
4. 任务失败如何记录
5. 后续问答和向量检索怎么接进来

只有数据模型稳住，Day 3 搭 Spring Boot 工程骨架时才不会乱。
