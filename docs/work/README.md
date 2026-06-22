# Work Index

## Purpose

`docs/work/` 用来沉淀各板块的工作推进资料，但它不替代：

1. 根目录 `README.md` 的项目总览。
2. `docs/rfcs/` 的长期工程决策。
3. 代码、测试和联调结果本身的真实实现状态。

这个目录主要回答五个问题：

1. 每个板块现在做到哪了。
2. 每个板块下一阶段准备做什么。
3. 这些能力是怎样逐步推进出来的。
4. 板块之间目前如何协同。
5. 某个决策应该去看状态、计划还是历史记录。

## Structure

当前按板块组织，每个板块固定三类材料：

1. `README.md`
   只做板块导航、阅读顺序和关联材料入口。
2. `current-status.md`
   只写当前事实、已验证内容、风险和下一步。
3. `plan.md`
   只写下一阶段目标、定位、推进重点、验证方向和约束。

`week*.md` 和 `work day*.md` 全部保留，继续作为历史推进记录，不再承担“当前口径”职责。

当前三个板块的 `plan.md` 已统一成同一类外层结构：

1. 当前进度快照
2. 目标
3. 当前定位
4. 下一阶段重点
5. 详细规划或分阶段推进
6. 测试与验证方向
7. 当前约束

## Boards

| Board | Position | Core Docs |
| --- | --- | --- |
| [rag-backend](./rag-backend/README.md) | Java RAG 主系统，负责知识库、文档、检索、问答、异步任务和运维语义 | [status](./rag-backend/current-status.md), [plan](./rag-backend/plan.md), `week*.md`, `work day*.md` |
| [rag-frontend](./rag-frontend/README.md) | RAG 工作台前端，负责知识库操作、检索调试、问答展示和运维入口 | [status](./rag-frontend/current-status.md), [plan](./rag-frontend/plan.md) |
| [rag-ai-service](./rag-ai-service/README.md) | Python AI Gateway，负责 embedding/chat provider 收口和模型能力治理边界 | [status](./rag-ai-service/current-status.md), [plan](./rag-ai-service/plan.md) |
| [rag-agent](./rag-agent/README.md) | LangGraph RAG 运维诊断 Agent 计划板块，负责下一阶段 Agent 编排、工具调用、human-in-the-loop 和执行轨迹设计 | [status](./rag-agent/current-status.md), [plan](./rag-agent/plan.md) |

## Reading Order

建议按这个顺序读：

1. 先看本页，建立全局目录感。
2. 再看对应板块的 `README.md`，确认该板块定位和入口。
3. 再看 `current-status.md`，建立当前口径。
4. 需要看下一阶段时，再看 `plan.md`。
5. 只有在还原具体实现背景时，再下钻到 `week*.md` 或 `work day*.md`。

## Cross-board Snapshot

截至 2026-05-18，仓库已经进入“多板块协同”阶段：

1. `rag-backend` 已完成企业级 RAG 主链路，覆盖文档接入、异步索引、向量检索、问答、readiness gate、rebuild 和恢复语义。
2. `rag-frontend` 已形成工作台式产品外壳，承接知识库、文档、检索、问答、历史和健康观察页面。
3. `rag-ai-service` 已把模型供应商调用从 Java 主系统中剥离出来，形成独立的 AI Gateway。
4. `rag-agent` 已完成下一阶段 LangGraph 运维诊断 Agent 的计划收口，但尚未进入代码实现。

当前项目的真实系统边界是：

1. Java 负责业务域和 RAG 编排。
2. Python 负责模型能力网关与 provider 适配。
3. 前端负责把主链路和运维动作组织成可操作、可演示的企业后台界面。
4. Agent 改造阶段将继续保持 Java 业务权威，Python 只承担 LangGraph Agent Runtime。

## Cross-board Milestones

1. Week 1 到 Week 3：后端完成文档入库、检索问答、异步索引、失败恢复、日志和评测基线。
2. Week 4：完成第一版 hybrid retrieval、前后端联调和最小观测口径。
3. Frontend Phase 1：工作台、文档、检索、问答、历史和健康页完成第一版接入。
4. AI Gateway Phase 1：`rag-ai-service` 落地，Java -> Gateway -> Provider 真实联调完成，并已统一收口为持续维护的 `plan.md`。
5. 真实运维链路：`health -> rebuild -> readiness -> retrieve -> ask` 已完成新架构下验证。
6. Agent Phase 0：已完成 LangGraph RAG 运维诊断 Agent 的 RFC、计划和恢复入口收口。

## Maintenance Rules

后续维护建议固定遵守这些规则：

1. `current-status.md` 只写“现在已经成立的事实”，不要混入长篇计划和逐日流水。
2. `plan.md` 只写“下一阶段准备做什么”，并保持“快照 + 目标 + 定位 + 推进重点 + 验证方向 + 约束”的统一入口结构。
3. `week*.md` 和 `work day*.md` 只承担历史追踪，不再作为当前状态入口。
4. 板块内链接优先使用相对路径，避免继续写绝对文件系统路径。
5. 如果长期行为发生变化，优先更新 `current-status.md` 和相关 RFC，而不是继续让旧周记充当现状说明。

## Next Documentation Tasks

当前文档系统下一步最值得继续做的是：

1. 给 `rag-backend` 增补一个更轻量的阶段索引，把 Week 和 Day 历史入口分组展示出来。
2. 逐步给前端和 AI Service 也补更明确的历史入口，而不是继续把所有沉淀都压在 `plan.md` 里。
3. 后续如果新增 `evaluation`、`ops`、`benchmark` 这类独立板块，继续沿用“README + current-status + plan + history”的结构。
