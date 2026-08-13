# rag-ai-service 开发计划

## 当前进度快照

截至当前仓库状态：

1. `rag-ai-service` 已完成第一版独立落地，不再只是方案设计。
2. `GET /health`、`POST /v1/embeddings`、`POST /v1/chat/completions` 已真实可用。
3. Java 后端已经通过 Gateway 调用 embedding 和 chat 能力，不再直接把供应商接入散落在业务主链路里。
4. 真实 `health -> rebuild -> retrieve -> ask` 链路已在新架构下联调通过。
5. 当前系统已经具备“Java 负责 RAG 业务域，Python 负责模型能力网关”的清晰边界。
6. 下一步重点不再是证明 Gateway 能不能工作，而是继续补 provider 治理、可观测性和后续扩展能力。

这个计划文档保留后续推进方向，当前真实状态以 [current-status.md](./current-status.md) 为准。

## 1. 目标

`rag-ai-service` 下一阶段的目标不是继续堆接口数量，而是：

**把它从“最小可用 AI Gateway”推进成“可扩展、可观测、可继续承接更多模型能力”的稳定服务边界。**

下一阶段要回答的核心问题包括：

1. 如何把 provider 差异继续收口，而不是重新泄漏回 Java 主系统。
2. 如何让 embedding、chat、rerank 的调用日志、错误分类和健康语义更稳定。
3. 如何在不破坏现有 RAG 主链路的前提下，为 vLLM、本地模型和 evaluation 预留真实扩展面。
4. 如何让这个服务在项目讲解和面试语境里，成为一个独立成立的工程亮点，而不是“顺手拆出去的一层 HTTP 转发”。

## 2. 当前定位

当前阶段，`rag-ai-service` 的定位固定为：

**项目的 Python AI Gateway。**

它负责的事情包括：

1. 统一承接 embeddings 和 chat completions 能力。
2. 统一适配 OpenAI-compatible provider，收口 base URL、API key、超时、重试和错误映射。
3. 透传 `X-Request-Id`，为主链路保留最小可观测口径。
4. 在已接入 rerank 的基础上，给后续 vLLM、本地模型和 evaluation 留出统一扩展面。

当前阶段它不负责的事情包括：

1. 知识库、文档、chunk、问答历史和状态机。
2. 检索编排、Prompt orchestration 和业务侧 readiness 规则。
3. 前端交互、运维工作台和业务流程组织。

这部分边界应继续保持稳定，不要为了方便把 Java 业务职责重新吸回 Python，也不要把 provider 细节重新泄漏回 Java。

## 3. 下一阶段重点

### 3.1 Provider 治理

下一阶段优先把当前单 provider 的最小实现，推进到“仍然简单、但扩展面清晰”的结构：

1. 明确 embedding、chat、rerank 的能力分层，不把不同能力继续混在同一套松散逻辑里。
2. 补齐 provider 级别配置约定，让“默认模型、超时、重试、开关”具备一致表达。
3. 继续保持 Java 只认 Gateway 契约，不直接依赖外部模型供应商地址和密钥。
4. 为后续多 provider 路由或 fallback 留结构位，但当前不急着把策略做重。

### 3.2 可观测性和错误语义

当前已经有最小日志口径，下一阶段要把“能看日志”推进到“足够定位问题”：

1. 补强 provider、model、latency、retry、usage、upstream status 等字段的一致性。
2. 让 timeout、429、5xx、参数错误、配置错误的分类更稳定，避免所有失败都混成同一种 provider error。
3. 统一健康检查语义，继续区分“服务存活”与“端到端模型能力可用”。
4. 为后续 metrics 或 tracing 接入预留字段和扩展点，但当前仍以最小日志观测为主。

### 3.3 能力扩展顺序

后续新增能力不应并行发散，建议按这个顺序推进：

1. 先补强现有 embedding/chat 的稳定性和可观测性。
2. 使用真实评测验证已收口的 rerank，并据此决定是否开启环境默认值。
3. 再考虑接入 vLLM 或本地模型，把部署差异继续隔离在 Gateway 内部。
4. 最后再决定 evaluation 是作为服务内能力、旁路工具，还是独立板块维护。

这个顺序的核心原则是：先把边界立稳，再扩功能，不为了“能力看起来更多”破坏当前主链路清晰度。

## 4. 分阶段推进

### Stage 1：稳定性收口

目标：把当前已上线的最小 Gateway 从“能跑”推进到“更稳”。

这一阶段优先完成：

1. 补齐 provider 级日志字段和错误分类。
2. 统一 embedding/chat 的超时、重试和异常映射口径。
3. 梳理配置项，避免后续新增 provider 时再次散开。
4. 复核 `/health` 与 Java `/api/health` 的职责边界，保证探针语义继续清晰。

验收标准：

1. 常见失败场景能从日志快速看出是配置、网络、限流还是上游错误。
2. Java 侧不需要知道 provider 细节，仍只依赖 Gateway 契约。
3. 现有 `rebuild / retrieve / ask` 主链路行为不退化。

### Stage 2：能力位扩展

目标：让 `rag-ai-service` 不只承接 embedding/chat，也成为后续模型能力的统一落点。

这一阶段优先评估和设计：

1. 完成 rerank 的真实排序与延迟评测，验证 Java fail-open、缓存和历史回放语义。
2. 是否接入 vLLM 或本地模型，形成“同一契约下多种部署来源”的统一入口。
3. 是否需要最小 provider 路由或 fallback 规则。
4. 哪些能力应该继续保留在 Gateway，哪些更适合旁路脚本或独立模块。

验收标准：

1. 新能力进入时不需要破坏现有 embedding/chat 契约。
2. Java 主链路不因为模型来源切换而出现大面积改动。
3. 新增能力的配置、日志和错误语义能沿用现有框架表达。

### Stage 3：面试化与资料收口

目标：把 `rag-ai-service` 从代码实现，推进到可讲透的工程资产。

这一阶段补齐：

1. 服务边界说明、调用链说明和配置说明。
2. 典型请求/响应样例与错误样例。
3. 为什么拆 Python Gateway、为什么不继续把 provider 调用留在 Java 的设计取舍。
4. 后续可扩展方向，包括 rerank、vLLM、本地模型和 evaluation 的进入路径。

验收标准：

1. 能独立讲清楚这个服务解决了什么问题。
2. 能解释清楚它与 `rag-backend` 的边界分工。
3. 能说明当前为什么先做到这个程度，以及下一步为什么按这个顺序演进。

## 5. 测试与验证方向

下一阶段持续验证的重点包括：

1. `GET /health` 仍只表达服务存活，不额外消耗 provider token。
2. `POST /v1/embeddings` 和 `POST /v1/chat/completions` 在正常、超时、限流、5xx 场景下都能返回稳定结构。
3. `X-Request-Id` 在 Java -> Gateway -> Provider 链路中继续保持透传和回传。
4. Java `/api/health` 仍能真实反映端到端 AI 能力可用性，而不是只验证网关进程活着。
5. 真实 `rebuild -> retrieve -> ask` 链路在 Gateway 继续演进后不能回退。

## 6. 当前约束

后续推进时，默认继续遵守这些约束：

1. `rag-ai-service` 仍以最小 OpenAI-compatible 子集为核心，不追求完整 OpenAI API 覆盖。
2. 现阶段优先保证边界清晰和主链路稳定，不主动引入过重的平台化能力。
3. Provider 细节应继续收口在 Python 层，不重新散回 Java 配置和业务逻辑。
4. 新能力只有在能复用现有契约、日志和错误语义时才应进入该服务。
5. 当前没有独立 metrics/tracing 平台时，日志字段质量本身就是核心工程资产，不能把它当成附属细节。
