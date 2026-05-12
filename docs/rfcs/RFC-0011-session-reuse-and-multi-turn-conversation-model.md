# RFC-0011 Session Reuse And Multi-turn Conversation Model

- Status: Planned
- Created: 2026-05-12
- Last Updated: 2026-05-12
- Owners: RAG Team

## Summary

本 RFC 记录系统从“单问单答 + 每次新建 session”演进到“session reuse + 多轮对话”的正式路线图。当前仓库已经具备 `chat_session / chat_message` 持久化与 `/qa/history` 回放，但它们服务的是单轮问答记录，而不是真正连续会话。

本 RFC 的核心结论是：

1. 当前历史模型必须继续被解释为“单问单答记录列表”。
2. 后续如果接入 session reuse，应该把它视为问答契约、持久化模型、前端历史结构和评测方式的联合变更，而不是只在接口里多传一个 `sessionCode`。
3. 在该能力真正落地前，README、前端页面和现有 RFC 仍应明确写明“还没有做 session 复用与多轮对话”。

## Context

当前系统已经具备最小问答闭环：

1. `POST /qa/ask` 可以完成检索、回答生成与结果持久化。
2. `chat_session / chat_message` 已落地到数据库。
3. `GET /qa/history` 已能查回真实问答记录。
4. 前端历史页已经可以展示问题、答案、来源和检索证据。

但这个模型有一个非常明确的边界：

1. 每次问答都会新建一个 `chat_session`。
2. 当前并不会复用已有 session。
3. 当前 session 名称只是从本次问题截取出的展示字段。
4. 当前问答 prompt 不读取历史消息，也没有多轮上下文拼接。

因此，现有“历史记录”更接近问答审计日志，而不是聊天线程。

## Decision

本 RFC 先确立未来演进方向，而不声称当前已经实现：

1. session reuse 将被视为正式产品能力，不再把每次问答都建成独立 session。
2. 多轮对话的最小定义是：后续问题可以绑定到已有 session，并在生成回答前读取该 session 的有限历史上下文。
3. 当 session reuse 真正落地时，`RFC-0007` 的历史契约、前端历史页结构和评测方式都需要同步修订。
4. 在能力落地前，继续维持当前单轮记录模型，避免让前端先按“连续聊天”错误建模。

## Historical Evolution

### Phase 1: 先设计会话与消息表

- 相关材料：[work day2.md](../../rag-backend/work/work%20day2.md)
- 特征：项目很早就为 `chat_session / chat_message` 预留了数据库模型，但当时还没有真实问答链路。

### Phase 2: 先落单轮问答历史

- 相关提交：`1750636` `persist qa history for day13 and prepare day14`
- 特征：`chat_session / chat_message` 和 `/qa/history` 真正落地，但保存的是单问单答快照，而不是多轮线程。

### Phase 3: 明确把多轮会话留在边界之外

- 相关材料：[README.md](../../README.md)、[RFC-0007](./RFC-0007-qa-contract-answer-sources-history.md)
- 特征：仓库已经明确写出“还没有做 session 复用与多轮对话”，前端历史页也因此按记录列表而不是聊天线程实现。

## Current State

当前实现位于 [QaRecordService.java](../../rag-backend/src/main/java/com/example/rag/service/QaRecordService.java)。

现有持久化行为是：

1. `persist()` 每次调用都会生成新的 `sessionId` 和 `sessionCode`。
2. 每次问答都会插入新的 `chat_session`。
3. 当前 `chat_message` 与 `chat_session` 之间是一对一使用关系，而不是“一个 session 下连续积累多条对话消息”的产品语义。
4. `/qa/history` 返回的是按消息时间倒序的单问单答证据快照。

当前前端历史页与问答页也都建立在这个假设上：

1. 历史页展示记录列表，不展示线程树。
2. 前端没有“继续本会话提问”的入口。
3. 当前没有 session 级上下文摘要、标题更新或消息轮次概念。

## Proposed Model

后续如果进入多轮能力实现，建议按下面模型演进：

1. `chat_session` 变成真正的会话容器，而不是单次问答外壳。
2. `chat_message` 在同一 session 下连续追加，至少要能区分 user / assistant 两类消息。
3. `POST /qa/ask` 或新的会话问答入口，需要支持“新建会话”和“复用已有会话”两种调用方式。
4. 问答编排层需要在生成回答前读取有限历史窗口，而不是只用当前问题做 retrieval 和 prompt assembly。
5. 历史读取接口需要逐步从“记录列表”演进到“会话列表 + 会话明细”。

这里的重点不是把系统强行改成通用聊天产品，而是让 RAG 问答支持最小可用的连续上下文。

## Implementation

真正实施时，至少会涉及四个层面。

### 1. Persistence

1. `chat_session` 需要明确生命周期、标题更新和最后活跃时间语义。
2. `chat_message` 需要支持一个 session 下多条连续消息，而不只是当前这种单问单答快照。
3. 现有历史记录里的 `retrievalResults` 和 `sources` 是否继续按 assistant message 持久化，需要明确保留。

### 2. API Contract

1. 问答入口需要支持“继续某个 session”。
2. 历史接口需要区分“会话列表”和“会话内消息列表”。
3. 需要定义 session 不存在、已归档或不属于当前知识库时的错误语义。

### 3. Frontend Model

1. 当前 `/history` 页面会从记录表演进成会话视图。
2. 当前问答页面需要增加“从历史继续提问”或“继续当前会话”的入口。
3. 现有 `sessionName` 展示方式可能需要改成会话标题，而不是首问截断。

### 4. Evaluation

1. `RFC-0009` 当前评测基线主要针对单轮问答。
2. 多轮问答落地后，需要新增上下文继承、指代消解和跨轮证据稳定性的评测样本。
3. rerank、retrieval trace 和 session reuse 后续会互相影响，评测不能继续只看单轮 query 命中率。

## Consequences

正面影响：

1. 系统能支持更真实的连续问答场景，而不只是一次性提问。
2. 前端历史页和问答页会更接近用户对“聊天式 RAG” 的直觉。
3. 后续 retrieval trace、prompt grounding 和会话级评测才有稳定承载层。

代价与约束：

1. 问答编排会从单轮调用演进成会话状态机，复杂度明显上升。
2. 历史记录、前端结构和评测基线都会被同时影响，不能只改一层。
3. 会话上下文一旦引入，就需要处理上下文长度、历史截断和错误记忆传播问题。

## Non-Goals

本 RFC 当前不定义：

1. 完整的通用聊天产品能力。
2. 长期记忆、用户画像或跨知识库共享会话。
3. 多租户会话隔离策略。
4. 具体 prompt memory 拼接算法。
5. 与外部 IM、客服工单或 agent orchestration 的集成。

## Open Questions

1. 继续提问时，retrieval query 是否只看当前用户输入，还是要拼接压缩后的历史上下文。
2. session 粒度是否严格绑定单个 knowledge base，还是未来允许跨知识库会话。
3. 是否需要把“会话继续提问”与“新会话提问”做成两个明确 API，而不是一个接口双语义。
4. 历史页是先保留现有记录列表，再加会话详情页，还是直接升级成双栏会话视图。
5. 多轮能力落地后，`RFC-0007` 是增量修订，还是拆分出新的会话契约 RFC。

## References

1. [README.md](../../README.md)
2. [RFC-0007](./RFC-0007-qa-contract-answer-sources-history.md)
3. [current-status.md](../../rag-backend/work/current-status.md)
4. [week2.md](../../rag-backend/work/week2.md)
5. [work day2.md](../../rag-backend/work/work%20day2.md)
6. [work day13.md](../../rag-backend/work/work%20day13.md)
7. [work day14.md](../../rag-backend/work/work%20day14.md)
8. [frontend plan.md](../../rag-frontend/work/frontend%20plan.md)
9. [QaRecordService.java](../../rag-backend/src/main/java/com/example/rag/service/QaRecordService.java)
