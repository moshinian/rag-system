# 当前状态

## 当前结论

`rag-ai-service` 已经完成 Phase 1 的第一版实现，不再只是方案设计。

当前它的真实角色是：

**作为 Java RAG 主系统前面的最小 AI Gateway，统一承接 embeddings 和 chat completions 能力，并把模型供应商边界从业务系统中剥离出来。**

当前已经完成的核心目标包括：

1. 独立 Python FastAPI 服务已落地。
2. `GET /health`、`POST /v1/embeddings`、`POST /v1/chat/completions` 已落地。
3. Java 后端已改为通过 Gateway 调用模型能力。
4. 真实健康检查、embedding rebuild、retrieve、ask 已完成联调验证。

## 已完成

### 服务结构

1. `app/main.py` 已作为服务入口落地。
2. `app/api/` 已收口健康检查和两个模型能力接口。
3. `app/core/` 已承接配置、异常处理和 requestId middleware。
4. `app/clients/` 已承接上游 OpenAI-compatible provider 调用。
5. `app/services/` 已承接 gateway 编排与日志。

### 对外契约

1. `GET /health` 已落地。
2. `POST /v1/embeddings` 已支持单输入和批输入。
3. `POST /v1/chat/completions` 已支持最小 OpenAI-compatible 子集。
4. 错误返回已统一成 `error.message / error.type / error.code` 结构。
5. `X-Request-Id` 已支持透传和回传。
6. `/health` 当前还会返回 `embedding/chat` 的 provider 与默认模型，便于 Java 健康页和前端页面展示实时运行配置。

### 上游能力适配

1. embeddings 已支持通过 OpenAI-compatible 协议调用 DashScope。
2. chat completions 已支持通过 OpenAI-compatible 协议调用 DeepSeek，也已验证可切换到阿里云百炼兼容模式。
3. HTTP timeout、有限重试和 provider 错误映射已落地。
4. usage 字段已按最小契约向下透出。

### Java 集成

1. Java 新增 `AiGatewayClient` 调用该服务。
2. `DocumentEmbeddingService` 已改走 Gateway。
3. `QuestionAnsweringService` 已改走 Gateway。
4. `ChatClient` 已改走 Gateway。
5. Java `/api/health` 已改为通过 Gateway 做真实能力探测。
6. Java 健康检查中的 `llm / embedding` provider 和 model 已改为透传 Gateway 当前运行配置，而不是只展示 Java 本地默认值。
7. Java chat 实际调用、`qa/ask` 返回值和 `qa/history` 落库的 `chatModel` 已与 Gateway 当前 `chat_default_model` 对齐。

## 已验证

当前已经完成过的真实验证包括：

1. Python 接口测试已通过。
2. Java 相关单测已完成 Gateway 改造后的验证。
3. 真实 `GET /api/health` 已验证 embedding / llm 都能通过 Gateway 返回可用状态。
4. 真实 `POST /api/admin/embeddings/rebuild` 已通过 Gateway 完成全量重嵌入。
5. 真实 `POST /qa/retrieve` 已通过 Gateway 生成 query embedding 并完成检索。
6. 真实 `POST /qa/ask` 已通过 Gateway 调用 chat provider 成功返回回答。
7. 2026-05-20 已完成一次真实切换验证：`CHAT_PROVIDER=aliyun-bailian-openai-compatible`、`CHAT_DEFAULT_MODEL=qwen-plus` 生效后，Gateway `/health`、Java `/api/health`、前端健康页、`qa/ask` 返回体与 `qa/history` 落库中的 `chatModel` 都已同步切换为 `qwen-plus`。

### 本次联调中确认的关键事实

1. Gateway 接入没有破坏原有 readiness gate。
2. 旧知识库在 embedding profile 切换后仍会被 `reembedRequired` 正常阻断。
3. 触发一次真实 rebuild 后，系统可以恢复到可检索、可问答状态。
4. `day20-cn-kb` 已在新 Gateway 口径下真实完成 `health -> rebuild -> retrieve -> ask` 联调。
5. `/health` 仍保持“只表达网关自身存活和当前运行配置”的轻量职责，不主动消耗 provider token。

## 当前未完成

1. 还没有 provider fallback、多 provider 路由或模型治理面板。
2. 还没有 rerank、evaluation、本地模型或 vLLM 接入。
3. 还没有独立指标系统或 tracing，只完成了最小日志观测。
4. 第一阶段仍以最小 OpenAI-compatible 子集为边界，还没有覆盖更完整的 API 面。

## 当前判断

当前 AI Service 板块的真实成熟度可以表达为：

1. Phase 1 已完成。
2. 它已经从“设计方案”变成“真实运行中的独立服务边界”。
3. 当前最重要的价值不是功能数量，而是模型供应商边界已经被收口，后续扩展不需要再污染 Java 主链。
4. 它已经足够作为简历和面试里的一个独立工程亮点来讲。

## 后续方向

建议 `rag-ai-service` 下一步优先推进：

1. 继续维护 `plan.md`，明确下一阶段 rerank / vLLM / evaluation 的进入顺序。
2. 增强 provider 级日志字段和失败分类。
3. 引入更稳定的健康探针和 provider 兼容策略沉淀。
4. 根据后端演进，决定是否把 rerank 和 evaluation 一起收口到这个服务。
