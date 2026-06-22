# 当前状态

## 当前结论

`rag-backend` 已经完成第一版企业级 RAG 主链路，并且当前真实架构已经升级为：

**Java RAG 主系统 + Python `rag-ai-service` AI Gateway + 外部 embedding / LLM provider。**

当前后端的定位不是“直接对接模型接口”，而是：

1. 管理知识库、文档、chunk、问答记录和审计信息。
2. 负责 retrieval、prompt orchestration 和问答主链路。
3. 负责异步索引、状态机、失败恢复、readiness gate 和 rebuild 语义。
4. 通过 `rag-ai-service` 调用 embedding 和 chat completion 能力。

## 已完成

### RAG 主链路

1. 知识库创建、启停、删除和恢复语义已落地。
2. 文档上传、解析、chunk 切分和入库已落地。
3. PostgreSQL + `pgvector` 向量写库已落地。
4. query embedding、TopK 检索、问答、来源返回和历史记录已落地。
5. `qa/readiness`、`qa/retrieve`、`qa/ask` 和 `qa/history` 已形成稳定接口。

### 工程化能力

1. 异步索引、索引状态机和任务阶段追踪已落地。
2. 失败重试、卡死任务恢复和恢复重试边界已落地。
3. `X-Request-Id`、结构化日志和最小缓存策略已落地。
4. embedding rebuild 和 `reembedRequired` 门禁已落地。
5. 文档禁用/恢复和知识库恢复的运维语义已落地。

### 检索增强

1. 第一版 dense retrieval 已稳定可用。
2. 第一版 hybrid retrieval、keyword retrieval 和 RRF fusion 已落地。
3. `retrievalMode / fusionStrategy` 已贯穿 retrieve、ask 和 history。
4. 前后端已经完成 `DENSE / HYBRID` 模式的真实联调。

### 架构演进

1. Java 已不再直接承接模型供应商调用。
2. 后端已通过 `AiGatewayClient` 接入 `rag-ai-service`。
3. 健康检查已经改成通过 Gateway 做端到端能力探测。
4. 健康检查中的 `aiGateway / embedding / llm` provider 和 model 已改为优先展示 Gateway 当前运行配置，而不是只读本地静态默认值。
5. chat completion 实际调用已优先跟随 Gateway 当前 `chat_default_model`；`qa/ask` 返回值、结构化日志和 `qa/history` 持久化的 `chatModel` 已保持一致。

## 已验证

当前已经真实验证过：

1. 文档上传 -> 异步索引 -> 检索 -> 问答 -> 历史查询的完整主链路。
2. `day20-cn-kb` 的中文问答评测和新架构下的 rebuild / readiness / retrieve / ask 联调。
3. `GET /api/health` 通过 Gateway 探测 embedding 和 llm 能力。
4. `POST /api/admin/embeddings/rebuild` 可完成一次全量重嵌入恢复。
5. 相关后端单测在 Gateway 重构后仍保持通过。
6. 2026-05-20 已完成一次真实问答闭环验证：当 Gateway chat 默认模型切换为 `qwen-plus` 后，`qa/ask` 返回体与 `qa/history` 最新记录中的 `chatModel` 都已同步变为 `qwen-plus`。

## 当前未完成 / 风险

1. hybrid retrieval 已完成第一版验收，但默认模式仍保守保持为 `DENSE`。
2. 评测集还不够大，专有名词、接口名、错误码等企业文档场景样本仍需继续补。
3. 更完整的 tracing、指标平台和日志采集体系还未落地。
4. 任务取消、批量编排和多实例任务协调还未开始。
5. 多轮会话和 session reuse 仍停留在 RFC 规划阶段。
6. embedding 仍保持显式模型配置，不跟随 Gateway 默认 embedding 模型自动漂移；这是为了维持向量维度、readiness 和 rebuild 语义稳定。

## 当前判断

当前后端阶段更准确的表达是：

1. 它已经不是“RAG demo 后端”，而是具备企业后台工程语义的第一版 RAG 主系统。
2. 业务域、检索编排和模型能力边界已经分清，拆分方式符合后续企业 AI 系统演进方向。
3. 现阶段最重要的增量不再是“继续堆接口”，而是继续扩大评测、增强观测、收紧运维边界。

## 下一步

建议后端下一阶段优先推进：

1. 扩大评测集，并把样本重点放在企业知识库常见的专有名词、接口名和错误码问题上。
2. 基于现有日志口径补 retrieval 与 LLM 的真实延迟对比，为默认模式切换提供依据。
3. 推进任务取消、批量编排和多实例协调的后台任务治理能力。
4. 结合 RFC 继续演进 session reuse / 多轮问答模型。
