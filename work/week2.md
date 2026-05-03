# 第 2 周执行清单

## 本周目标

第 2 周只做一件事：

**把 RAG 的问答主链路跑通。**

本周结束时，你至少要做到：

1. embedding 模型已经接入
2. chunk 向量已经可以落库
3. 基础 TopK 检索已经可运行
4. 大模型问答已经可运行
5. 返回结果里已经带引用来源
6. 问答记录已经可以保存和查询

## 当前阶段结论

Week 2 已完成第一版收口。

当前已经完成：

1. chunk 向量写库
2. query embedding
3. 第一版 TopK 检索
4. 第一版问答接口
5. 第一版 `sources` 来源返回
6. 第一版问答记录持久化
7. 第一版 `/qa/history`
8. Day 14 端到端验收

第 2 周已经打通的闭环是：

```text
问题输入 -> query embedding -> TopK 检索 -> Prompt 组装 -> LLM 回答 -> 引用来源返回 -> 问答记录保存
```

这意味着 Week 2 的主目标已经达成。

## 当前已具备的 Week 2 起点

进入第 2 周前，仓库已经具备下面这些基础：

1. 知识库创建链路已可运行
2. 文档上传链路已可运行
3. `md / txt / pdf` 第一版解析已完成
4. 第一版切块与 chunk 入库已完成
5. `document_chunk` 与 `indexing_task` 已完成真实写库验证
6. Day 6 真实联调与字段校验已完成
7. Day 7 README、架构图和阶段文档已完成收口

这意味着第 2 周当前不是从零开始做 RAG，而是在已有 chunk 数据基础上继续往检索和问答推进。

## 本周必须完成

### 1. embedding 接入

这部分要完成：

1. 选定 embedding 模型
2. 明确调用方式和配置项
3. 打通 chunk 文本到向量生成链路
4. 明确失败场景和重试边界

### 2. 向量存储

这部分要完成：

1. 选定 `pgvector` 方案
2. 设计向量字段和索引方案
3. 补齐向量落库迁移
4. 让 chunk 与向量建立稳定关联

### 3. 检索链路

这部分要完成：

1. 对 query 生成 embedding
2. 实现 TopK 相似度检索
3. 返回基础召回片段
4. 保留文档、chunk、分数等元数据

### 4. 问答链路

这部分要完成：

1. 设计第一版 Prompt 模板
2. 接入大模型回答
3. 组装上下文
4. 返回最终答案
5. 返回引用来源

### 5. 问答记录

这部分要完成：

1. 设计问答记录表或最小数据结构
2. 保存问题、答案、引用来源、模型信息
3. 提供最小查询能力

## 本周验收标准

第 2 周结束时，至少要达到：

1. 可以针对指定知识库发起提问
2. 可以基于已入库 chunk 做召回
3. 可以返回最终答案和引用片段
4. 问答记录可以查询
5. 你能把第 2 周的 RAG 问答链路讲清楚

## Day 8：进入 Week 2

今天要完成：

1. 明确 embedding 模型选择
2. 明确 `pgvector` 作为向量存储方案
3. 拆出检索与问答主链路的最小实现顺序
4. 明确需要新增的数据结构、迁移和接口
5. 开始第 2 周第一批实现

## Day 9：向量落库

今天要完成：

1. 补齐向量字段与迁移
2. 完成 chunk 向量写库
3. 设计向量更新策略

## Day 10：基础检索

今天要完成：

1. 实现 query embedding
2. 实现 TopK 相似度检索
3. 输出召回结果与分数

当前结果：

1. `POST /api/knowledge-bases/{kbCode}/qa/retrieve` 已落地
2. `QuestionAnsweringService` 已接入 query embedding
3. `DocumentChunkMapper` 已支持 `pgvector` TopK 查询
4. `day6-kb` 已返回真实召回结果

## Day 11：问答组装

今天要完成：

1. 设计 Prompt 模板
2. 拼装检索上下文
3. 接入大模型回答

当前结果：

1. `POST /api/knowledge-bases/{kbCode}/qa/ask` 已落地
2. `QaService / PromptBuilder / ChatClient` 已完成拆分
3. OpenAI-compatible chat 配置已支持切换 DeepSeek / vLLM / OpenAI
4. DeepSeek `deepseek-v4-pro` 已完成真实联调

## Day 12：引用来源

今天要完成：

1. 设计引用来源返回结构
2. 绑定文档、chunk、片段位置
3. 完成接口输出

当前结果：

1. `QaSourceResponse` 已落地
2. `/qa/ask` 已新增 `sources`
3. `sources` 已包含 `documentCode / documentName / chunkId / chunkIndex / content / score / startOffset / endOffset`

## Day 13：问答记录

今天要完成：

1. 保存问题与答案
2. 保存引用来源和模型信息
3. 提供历史查询能力

当前结果：

1. `chat_session / chat_message` 已完成迁移
2. `/qa/ask` 已接入问答记录持久化
3. `GET /api/knowledge-bases/{kbCode}/qa/history` 已落地
4. 历史接口已查回真实问答记录

## Day 14：联调验收

今天已完成：

1. 使用 `day14-kb` 跑通了创建知识库、上传文档、处理、embedding、检索、问答、历史查询整条链路
2. `POST /qa/ask` 已对真实问题返回正确答案
3. `POST /qa/ask` 已对无关问题返回“根据当前检索内容，无法确定答案。”
4. `GET /qa/history` 已查回真实问答记录

## Week 2 结束结论

第 2 周已经完成第一版收口，当前系统已具备：

1. 基于知识库的文档问答能力
2. 基于 `pgvector` 的向量检索能力
3. 基于 OpenAI-compatible 的 LLM 回答能力
4. `sources` 结构化来源返回能力
5. 问答记录持久化与历史查询能力

后续如果继续推进，重点就不再是“把主链路做出来”，而是：

1. 评测与效果优化
2. 混合检索
3. session 复用与多轮对话
4. 更完整的工程化能力
