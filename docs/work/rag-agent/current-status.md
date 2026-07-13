# 当前状态

## 当前结论

`rag-agent` 目前已经完成 Python Runtime 通过 LangChain MCP 工具调 Java 真实工具 API，并通过 Agent run 主链路完成 Java / Python / 前端闭环验证。

当前已经明确的方向是：

**基于 LangGraph Tool-use graph，并在 node 内使用 LangChain 能力的 RAG 运维诊断 Agent。**

它不是替代现有 RAG 主链路，也不是新增一个泛聊天机器人，而是在已有 RAG 系统上方增加 Agent 编排层：

1. Java 后端继续作为业务权威和 Agent Run 状态中心。
2. Python `rag-ai-service` 使用 LangGraph graph 承载 model/tool loop。
3. 前端新增 Agent 工作台，展示诊断轨迹、推荐动作和确认执行。
4. v1 聚焦两个可演示业务场景：`reembedRequired` 和 `FAILED indexing task`。

## 已完成

### Phase 0：计划和边界收口

1. 已确认 Agent 主场景是“RAG 运维诊断 Agent”。
2. 已确认 Java / Python / 前端三层分工。
3. 已确认 Python Agent Runtime 只负责诊断和推荐，不直接写业务库。
4. 已确认 Java 统一生成 `runCode / stepCode / actionCode`。
5. 已确认 `WAITING_CONFIRMATION` 是 Java run 状态，不要求 LangGraph 在确认后继续执行。
6. 已确认 `agent_run / agent_step / agent_action` 三张表作为轨迹和审计模型。
7. 已确认 v1 不做 MCP、多 Agent、多轮聊天和自动危险操作。

### Day 1：状态模型、工具协议和三张表

1. 已新增 Flyway 迁移 `V18__create_agent_tables.sql`，创建 `agent_run / agent_step / agent_action` 三张表。
2. 已新增 Agent 相关枚举：
   - `AgentRunStatus`
   - `AgentStepType`
   - `AgentStepStatus`
   - `AgentActionRiskLevel`
   - `AgentActionStatus`
   - `AgentToolExecutionMode`
3. 已新增 Agent 持久化实体、Mapper 和 Repository：
   - `AgentRunEntity / AgentRunMapper / AgentRunRepository`
   - `AgentStepEntity / AgentStepMapper / AgentStepRepository`
   - `AgentActionEntity / AgentActionMapper / AgentActionRepository`
4. 已新增 Java 与 Python Agent Runtime 的第一版协议 DTO：
   - `AgentRuntimeRequest`
   - `AgentRuntimeResponse`
   - `AgentRuntimeStepResult`
   - `AgentRuntimeActionDraft`
   - `AgentToolDefinition`
5. 已在 `rag-ai-service` 新增 `app/agent/` 包，并补入 Python 侧 `AgentState` 与 Runtime 请求/响应模型草案。

### Day 2：Agent 查询 API 与 Service 骨架

1. 已新增 `AgentRunCreateRequest`。
2. 已新增 `AgentRunResponse / AgentStepResponse / AgentActionResponse`。
3. 已新增 `AgentRunService`，支持：
   - 创建 Agent run
   - 查询 Agent run 详情
   - 查询详情时组装 steps/actions
   - 校验 run 必须属于当前知识库
4. 已新增 `AgentController`，支持：
   - `POST /api/knowledge-bases/{kbCode}/agent/runs`
   - `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}`
5. 已新增 `AgentRunServiceTest / AgentControllerTest`。
6. Day 2 继续保持不调用 Python Agent Runtime、不实现 confirm/reject、不接前端。

### Day 3：P0 只读工具封装

1. 已新增 Agent 工具执行基础对象：
   - `AgentToolContext`
   - `AgentToolResult`
2. 已新增 Java 侧 Agent 工具抽象与注册表：
   - `AgentTool`
   - `AgentToolRegistry`
   - `AgentToolSupport`
3. 已封装两个 P0 只读工具：
   - `system.health.check`
   - `kb.readiness.check`
4. 两个工具都声明为：
   - `executionMode = READ_ONLY`
   - `maxRiskLevel = LOW`
5. 已新增工具层测试：
   - `AgentToolRegistryTest`
   - `SystemHealthAgentToolTest`
   - `QaReadinessAgentToolTest`
6. 已修复 `AgentControllerTest` 中 `MediaType.APPLICATION_JSON` 触发的 null-safety IDE 诊断。

### Day 4：Python LangGraph 最小诊断图

1. 已在 `rag-ai-service` 引入 `langgraph` 依赖。
2. 已新增 Python Agent Runtime：
   - 早期 `app/agent/graph.py` 已重建为当前统一 LangGraph 智能图 facade
   - `app/agent/runtime.py`
   - `app/agent/tools.py`
3. 早期最小 readiness 固定图已下线，当前 Python Runtime 统一使用 LangGraph Tool-use graph：
   - LangGraph `agent_model` node 内的 LangChain model update 映射为 `LLM_DECISION`
   - Java MCP tool 调用映射为 `TOOL_CALL`
   - request_* action tool 映射为 `ACTION_RECOMMENDED`
4. 已新增 `POST /v1/agent/runs`。
5. 当时已实现 Day 4 可替换工具客户端 `StaticAgentToolClient`，用于在 Java 内部工具 HTTP API 接入前验证 Runtime 形状；当前已由 MCP tool client / LangChain tool catalog 替代。
6. 已支持 `reembedRequired=true` 时生成 `embedding.rebuild.submit` action 草案。
7. 旧诊断模式已从当前协议中移除；当前协议不再暴露 run mode。
8. Python Runtime 继续保持：
   - 不生成 `stepCode / actionCode`
   - 不写业务库
   - 不执行重嵌入、重试等写操作
   - 只返回步骤、诊断 summary 和 action 草案

### Day 5：Java 调用 Python Runtime 并落库

1. 已新增 Java 侧 Python Agent Runtime 客户端：
   - `AgentRuntimeClient`
2. 已在 `rag.ai.gateway` 配置中新增：
   - `agent-runs-path=/v1/agent/runs`
3. `AgentRunService.createRun` 已从“只创建 RUNNING run”升级为：
   - 创建 `agent_run`
   - 调用 Python `POST /v1/agent/runs`
   - 持久化 Runtime 返回的 steps
   - 持久化 Runtime 返回的 recommendedActions
   - 更新 run 的 `summary/status/errorMessage/finishedAt`
4. Java 继续统一生成：
   - `runCode = AR-...`
   - `stepCode = AST-...`
   - `actionCode = ACT-...`
5. Runtime 返回待确认 action 时，run 进入：
   - `WAITING_CONFIRMATION`
6. Runtime 成功但没有 action 时，run 进入：
   - `SUCCEEDED`
7. Runtime 返回失败或调用异常时，run 进入：
   - `FAILED`
8. Day 5 仍不执行任何写操作，也不实现 confirm/reject。

### Day 6：documents/status 与 indexing/tasks 扫描

1. 已新增 Java 侧 P1 只读工具：
   - `documents.status.scan`
   - `indexing.tasks.scan`
2. 两个新工具都声明为：
   - `executionMode = READ_ONLY`
   - `maxRiskLevel = LOW`
3. `documents.status.scan` 已支持：
   - 按知识库扫描文档
   - 聚合 `DocumentStatus` 计数
   - 返回失败文档样本
4. `indexing.tasks.scan` 已支持：
   - 按知识库扫描索引任务
   - 聚合 `IndexingTaskStatus` 计数
   - 返回 `FAILED` 任务样本
   - 补充 `documentCode` 供后续 action payload 和前端展示使用
5. 已扩展 Python LangGraph：
   - `documents_status_scan`
   - `indexing_tasks_scan`
6. 已新增 `FAILED_INDEXING_TASK` 诊断规则。
7. 存在失败索引任务时，Python Runtime 会返回：
   - `document.indexing_task.retry` action 草案
8. Day 6 仍不执行 retry，只推荐待确认动作。

### Day 7：演示场景端到端验收

1. 已新增 `AgentRunScenarioTest`，固定两个核心演示场景：
   - `reembedRequired -> embedding.rebuild.submit -> WAITING_CONFIRMATION`
   - `FAILED indexing task -> document.indexing_task.retry -> WAITING_CONFIRMATION`
2. 已确认 Python Runtime 在 readiness 异常场景返回：
   - `embedding.rebuild.submit`
   - `requiresConfirmation=true`
   - `riskLevel=MEDIUM`
3. 已确认 Python Runtime 在索引异常场景返回：
   - `document.indexing_task.retry`
   - `actionPayload` 包含 `taskId/documentCode`
   - 不包含 `actionCode`
4. 已确认 Java 落库后：
   - run 进入 `WAITING_CONFIRMATION`
   - action 进入 `PENDING_CONFIRMATION`
   - Java 生成 `ACT-...`
   - Java 生成 `AST-...`
5. Day 7 仍未执行任何写操作，也未实现 confirm/reject。

### Day 8：document.indexing_task.retry 确认执行

1. 已新增 Agent action 确认/拒绝请求 DTO：
   - `AgentActionConfirmRequest`
   - `AgentActionRejectRequest`
2. 已在 `AgentController` 新增：
   - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm`
   - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject`
3. 已在 `AgentRunService` 实现 `document.indexing_task.retry` 确认执行：
   - 校验 knowledge base / run / action 归属
   - 校验 run 必须是 `WAITING_CONFIRMATION`
   - 校验 action 必须是 `PENDING_CONFIRMATION`
   - 校验 action 必须 `requiresConfirmation=true`
   - 校验 toolName 白名单只允许 `document.indexing_task.retry`
   - 拒绝 `HIGH` risk action
4. confirm 成功时由 Java 调用：
   - `DocumentIndexingService.retry(kbCode, documentCode, taskId, operator)`
5. confirm 成功后：
   - action 进入 `SUCCEEDED`
   - `resultJson` 写入 retry task response
   - run 进入 `SUCCEEDED`
6. confirm 业务失败后：
   - action 进入 `FAILED`
   - `errorMessage` 写入失败原因
   - run 进入 `FAILED`
7. reject 后：
   - action 进入 `REJECTED`
   - run 进入 `SUCCEEDED`
8. Day 8 仍明确不执行：
   - `embedding.rebuild.submit`
   - 任何 `HIGH` risk action
9. Python Runtime 继续只生成 action 草案，不参与确认后的执行。

### Day 9：embedding.rebuild.submit 确认执行

1. 已扩展 `AgentRunService.confirmAction` 的执行分发能力。
2. confirm 白名单目前支持：
   - `document.indexing_task.retry`
   - `embedding.rebuild.submit`
3. 已接入现有业务入口：
   - `EmbeddingRebuildService.submit(operator)`
4. 已明确 Day 9 的设计边界：
   - 当前业务入口是全量重嵌入，不是单知识库重嵌入
   - path `kbCode` 用于 run/action 归属校验
   - payload `kbCode` 如果存在，必须和 path `kbCode` 一致
   - Python 不生成 rebuild run id
5. confirm `embedding.rebuild.submit` 成功后：
   - action 进入 `SUCCEEDED`
   - `resultJson` 写入 `EmbeddingRebuildSubmitResponse`
   - run 进入 `SUCCEEDED`
6. confirm `embedding.rebuild.submit` 业务失败后：
   - action 进入 `FAILED`
   - `errorMessage` 写入失败原因
   - run 进入 `FAILED`
7. 继续拒绝：
   - `HIGH` risk action
   - 未知 toolName
   - `requiresConfirmation=false` 的 action
8. 两个优先演示场景现在都具备确认执行闭环：
   - `reembedRequired -> embedding.rebuild.submit -> confirm`
   - `FAILED indexing task -> document.indexing_task.retry -> confirm`

### Day 10：前端 Agent 工作台最小闭环

1. 已新增前端 Agent 类型：
   - `rag-frontend/src/types/agent.ts`
2. 已新增前端 Agent API 封装：
   - `createAgentRun(kbCode, payload)`
   - `getAgentRun(kbCode, runCode)`
3. 已新增 Agent 工作台页面：
   - `/kb/:kbCode/agent`
4. 页面已支持：
   - 输入诊断目标
   - 输入可选问题
   - 填写 createdBy
   - 创建 Agent run
   - 输入 runCode 查询 Agent run
   - 刷新当前 run
5. 页面已展示：
   - run status
   - summary
   - errorMessage
   - run 基本信息
   - steps 基础表格
   - actions 基础表格
6. 已新增侧边栏入口：
   - `Agent 诊断`
7. Day 10 仍未实现：
   - timeline 视觉化
   - action 推荐卡片
   - confirm/reject UI
8. 前端继续只调用 Java API，不直接调用 Python Runtime。

### Day 11：Agent timeline 与推荐动作卡片

1. 已新增 Agent 执行轨迹组件：
   - `rag-frontend/src/components/agent/agent-step-timeline.tsx`
2. 已新增 Agent 推荐动作卡片组件：
   - `rag-frontend/src/components/agent/agent-action-cards.tsx`
3. Agent 页面已从基础表格升级为：
   - 诊断摘要
   - 执行轨迹 timeline
   - 推荐动作 cards
   - 原始 steps 表格
   - 原始 actions 表格
4. Timeline 已展示：
   - nodeName
   - stepType
   - status
   - toolName
   - durationMs
   - errorMessage
   - inputJson / outputJson 折叠查看
5. 推荐动作卡片已展示：
   - title
   - toolName
   - actionCode
   - riskLevel
   - requiresConfirmation
   - status
   - reason
   - actionPayload / resultJson 折叠查看
   - confirmedBy / confirmedAt / executedAt
6. Day 11 仍未实现 confirm/reject UI。
7. 前端继续只调用 Java API，不直接调用 Python Runtime。

### Day 12：前端 confirm/reject 与执行结果展示

1. 已扩展前端 Agent 类型：
   - `AgentActionConfirmPayload`
   - `AgentActionRejectPayload`
2. 已扩展前端 Agent API 封装：
   - `confirmAgentAction(kbCode, runCode, actionCode, payload)`
   - `rejectAgentAction(kbCode, runCode, actionCode, payload)`
3. Agent 页面已接管 action 人审状态：
   - confirm/reject loading actionCode
   - confirm/reject error 展示
   - confirm/reject 成功后用后端返回的 `AgentRun` 刷新当前页面
4. 推荐动作卡片已支持：
   - `PENDING_CONFIRMATION + requiresConfirmation=true` 时展示操作入口
   - `confirm` 二次确认
   - `reject` 拒绝原因输入
   - `HIGH` risk 动作禁用直接 confirm，并展示风险提示
5. 执行结果展示已形成闭环：
   - action 状态刷新后继续展示 `status`
   - `resultJson` 继续通过折叠面板展示
   - `errorMessage` 继续通过错误提示展示
   - `confirmedBy / confirmedAt / executedAt` 继续展示
6. Day 12 继续保持：
   - 前端只调用 Java Agent confirm/reject API
   - 前端不直接调用 Python Runtime
   - 前端不直接调用 indexing retry 或 embedding rebuild 等业务写 API
   - 前端不生成 `runCode / stepCode / actionCode`

### Day 13：`qa.retrieve.probe` Dense / Hybrid 检索探测

1. 已新增 Java 侧 Agent 只读工具：
   - `QaRetrieveProbeAgentTool`
   - toolName = `qa.retrieve.probe`
   - `executionMode = READ_ONLY`
   - `maxRiskLevel = LOW`
2. `qa.retrieve.probe` 已复用现有 Java 检索入口：
   - `QuestionAnsweringService.retrieve(kbCode, question, 5, RetrievalMode.DENSE)`
   - `QuestionAnsweringService.retrieve(kbCode, question, 5, RetrievalMode.HYBRID)`
3. 工具输出已包含：
   - Dense / Hybrid 命中数
   - Dense / Hybrid 耗时
   - `fusionStrategy`
   - TopK source 摘要
   - `denseEmpty / hybridEmpty / keywordZeroHit / hybridNoGain / topSourceChanged` signals
4. 工具输出会裁剪 chunk 内容：
   - 保留 `documentCode / documentName / chunkId / chunkIndex / score`
   - 不把完整 chunk content 写入 Agent step
5. 当时已扩展 Python `StaticAgentToolClient`，当前已由 MCP tool client / LangChain tool catalog 替代：
   - 支持 `qa.retrieve.probe`
   - 继续只返回工具观察结果，不实现真实检索算法
6. 当时已扩展 Python LangGraph，当前已由统一 LangGraph 智能图替代：
   - 新增 `qa_retrieve_probe` 节点
   - 位于 `indexing_tasks_scan -> qa_retrieve_probe -> diagnose`
   - 有 question 时执行 probe
   - 无 question 时记录 `SKIPPED` step，不编造问题
7. 已扩展诊断规则：
   - readiness / failed indexing task 仍优先
   - Dense + Hybrid 都空时诊断为 `RETRIEVAL_NO_HITS`
   - Hybrid keyword 零命中时诊断为 `RETRIEVAL_KEYWORD_ZERO_HIT`
8. Day 13 没有新增写 action。
9. Python Runtime 继续不返回：
   - `runCode`
   - `stepCode`
   - `actionCode`

### Day 14：Python 调 Java 真实工具 HTTP API 与端到端联调

1. 已新增 Java 内部 Agent 工具 HTTP API：
   - `POST /api/internal/agent/tools/{toolName}/execute`
   - 请求头：`X-Agent-Tool-Token`
   - 请求体由 Python Runtime 传入 `runCode / kbCode / question / operator / attributes`
2. Java 内部工具入口保持业务边界：
   - 只执行 `AgentToolRegistry` 中注册的工具
   - 只允许 `executionMode = READ_ONLY`
   - 写动作不暴露给 Python Runtime 直接执行
3. 当时已新增 Python `JavaAgentToolClient`；当前 Java 工具统一通过 LangChain MCP adapter 访问。
4. 已修复本地端到端联调时的代理问题：
   - `httpx` 默认 `trust_env=True` 会让 `127.0.0.1:8080` 走环境代理并返回空 503
   - Java 内部工具 client 已改为 `trust_env=False`
   - 外部模型 client 不受该变更影响
5. 已完成真实服务端到端联调：
   - Python `rag-ai-service` 运行在 `127.0.0.1:8001`
   - Spring Boot 运行在 `8080`
   - Spring Boot 启动参数：`--rag.ai.gateway.base-url=http://127.0.0.1:8001`
   - Python 启动参数：`AGENT_TOOL_CLIENT=java`
6. 已验证 `GET /api/health`：
   - `postgres = UP`
   - `redis = UP`
   - `embedding = UP`
   - `llm = UP`
   - embedding / llm endpoint 均指向 `http://127.0.0.1:8001`
7. 已通过 `finance-kb` 完成真实 Agent Run：
   - runCode = `AR-325284142981976065`
   - run status = `WAITING_CONFIRMATION`
   - summary = `知识库存在失败的索引任务，需要人工确认后重试失败任务。`
8. 该 run 已确认真实执行并持久化以下 steps：
   - `system_health_check`
   - `kb_readiness_check`
   - `documents_status_scan`
   - `indexing_tasks_scan`
   - `qa_retrieve_probe`
   - `diagnose`
   - `recommend_actions`
   - `generate_report`
9. `FAILED indexing task` 演示场景已打通：
   - `indexing.tasks.scan` 扫描到 `FAILED=4`
   - `diagnose.primaryCause = FAILED_INDEXING_TASK`
   - 生成 `document.indexing_task.retry` action 草案
   - action 状态为 `PENDING_CONFIRMATION`
   - actionCode 由 Java 生成：`ACT-325284170513387521`
10. `qa.retrieve.probe` 真实工具链路已打通：
    - Dense 命中 5 条
    - Hybrid 命中 5 条
    - signals 包含 `keywordZeroHit=true / hybridNoGain=true`
11. 已验证 Python 不能直接执行写动作：
    - 内部工具入口不注册 `document.indexing_task.retry`
    - 写动作只在 Java `AgentRunService.confirmAction` 中按白名单和人审状态执行

## 第 3 周智能 Tool-use 进度

1. 第 3 周智能 Tool-use Agent 已完成 Day 15-20 的基础骨架：
   - 当前已取消 run mode 字段，统一使用智能 Tool-use Agent。
   - 已移除 legacy 固定图入口 `build_readiness_diagnosis_graph()`。
   - 当前 Python 主路径已由 LangGraph graph 接管。
   - `rag-ai-service` 已引入 LangChain 1.x / LangGraph 1.2.x 作为 Agent Runtime 依赖。
   - 生产 Runtime 默认使用 LangGraph graph；node 内使用 LangChain `ChatOpenAI`、tool binding 和 structured response。
   - MCP tools discovery 和 execution 已统一使用 LangChain MCP 工具路径。
   - Python 侧自研 MCP JSON-RPC client 已移除；Agent runtime 元数据通过 LangChain tool interceptor headers 传给 Java。
   - AgentState 已收口为协议文档模型；运行时状态由 LangGraph `AgentGraphState` 管理。
   - Agent step type 已补入 `LLM_DECISION`，避免把结构化决策误表述为 chain-of-thought。
   - Tool Definition 已升级为 v2，补入 `schemaVersion / description / inputSchema / outputSchema / sourceType / requiresConfirmation / timeoutMs`。
   - Java 已暴露 `GET /api/internal/agent/tools`，供 Python Runtime 发现 Java tools。
   - Python Runtime 已支持 LangGraph 主循环、只读工具调用、recommended action 强制拦截、fake MCP tool 和只读 CLI tool。
   - Day 18 已补充 unknown tool、arguments schema mismatch、最大工具调用次数等 fake/mock LLM 失败恢复测试。
   - Day 19 已补充 Java 智能模式 recommended action -> `WAITING_CONFIRMATION` 的专项测试。
   - Day 20 已将 fake MCP tool 和只读 CLI tool 推进到 settings 配置化最小 MVP。
2. 已补齐 Day 20/21 联调前发现的两个缺口：
   - 前端 step type 已支持 `LLM_DECISION`。
   - 当前 Python 侧通过 LangChain MCP adapter 发现和执行 Java tools。
3. 已完成一次 frontend -> backend -> ai-service 的真实联调：
   - 前端 dev server 通过 Vite proxy 调 Java。
   - Java 创建 Agent run。
   - Python 拉取 Java Tool Registry definitions，并合并 fake MCP / CLI tools。
   - 智能主循环执行 `mcp.repo.status.inspect -> cli.git.status -> FINAL_ANSWER`。
   - run `AR-327300621860474881` 返回 `SUCCEEDED`，无 recommended actions。

## 当前未默认启用 / 未实现

1. LangGraph native streaming adapter 已在 Python Runtime 内实现，可通过 `agent_streaming_mode=langgraph` 启用；默认仍使用当前 `QueueAgentEventSink` 稳定路径，Java SSE 映射层不变。
2. LangGraph interrupt/checkpointer 尚未接入；recommended action 仍由 Java action 表和确认 API 作为权威状态。
3. 前端对 `qa.retrieve.probe` 仍复用 step `outputJson` 展示，尚未做专门 Dense / Hybrid 对比组件。
4. `reembedRequired` 场景已有测试闭环，但真实演示仍需要准备确定性数据，确保 readiness 返回 `reembedRequired=true`。
5. `FAILED indexing task` 场景已有测试闭环，但真实演示仍需要准备至少一条 `FAILED` indexing task。

## 当前风险

1. `Java -> Python -> Java` 链路会增加接口契约复杂度，需要坚持“Java 是权威状态中心”的边界。
2. 写操作必须 human-in-the-loop，不能为了演示效果绕过确认和白名单。
3. 三端联调依赖本地启动环境变量；已按 `.vscode/launch.json` 读取模型 key 后复测，Java `/api/health` 整体为 `UP`。
4. Day 21 演示场景必须准备确定性数据，避免真实环境状态变化导致 expected timeline 跑偏。

## 下一步

从 [plan.md](./plan.md) 的后续项继续：

1. 按第 3 周 Day 21 整理固定演示问题、期望 timeline 和面试材料。
2. 准备 `reembedRequired=true` 和 `FAILED indexing task` 的确定性演示数据。
3. 后续在真实三端联调中灰度验证 `agent_streaming_mode=langgraph`，确认事件顺序和 Java 落库补发完全兼容后再考虑切默认。
4. 后续仅在需要“确认后回到同一 graph 继续执行”时，设计 LangGraph interrupt/checkpointer 与 Java AgentRun/Action 权威状态的映射。

## 已验证

本轮将 Python Agent 主循环纠偏为 LangGraph graph 后已验证：

1. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过：16 passed。
2. `./.venv/bin/python -m pytest rag-ai-service/tests` 已通过：24 passed, 1 skipped。
3. 已补充 LangGraph native `custom/updates` stream adapter 单测，确认仍输出既有 `AgentRuntimeEvent` SSE 协议。
4. 已补充 LangChain structured-output 兼容形状单测，确认不通过 LangChain `create_agent` 接管主循环。

历史验证记录：

1. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过：14 passed。
2. `./.venv/bin/python -m pytest rag-ai-service/tests` 已通过：22 passed, 1 skipped。
3. `mvn -q -pl rag-backend -Dtest=AgentRuntimeStreamingClientTest,AgentRunEventApplierTest,AgentRunServiceTest,AgentControllerTest test` 已通过。
4. `mvn -q -pl rag-backend test` 已通过。
5. `cd rag-frontend && npm run build` 已通过。
6. `git diff --check` 已通过。

1. `mvn -q -pl rag-backend -DskipTests compile` 已通过。
2. `./.venv/bin/python -m py_compile rag-ai-service/app/agent/__init__.py rag-ai-service/app/agent/state.py` 已通过。
3. `mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest test` 已通过。
4. `mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,SystemHealthAgentToolTest,QaReadinessAgentToolTest test` 已通过。
5. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过。
6. `./.venv/bin/python -m pytest rag-ai-service/tests/test_app.py` 已通过。
7. `./.venv/bin/python -m py_compile rag-ai-service/app/agent/state.py rag-ai-service/app/agent/tools.py rag-ai-service/app/agent/runtime.py rag-ai-service/app/api/routes.py` 已通过。
8. `mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentControllerTest,AgentRuntimeClientTest test` 已通过。
9. `mvn -q -pl rag-backend -DskipTests compile` 已通过。
10. `mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest,AgentRunServiceTest,AgentControllerTest,AgentRuntimeClientTest test` 已通过。
11. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py` 已通过。
12. `mvn -q -pl rag-backend -Dtest=AgentRunScenarioTest,AgentRunServiceTest,AgentControllerTest,AgentRuntimeClientTest,AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test` 已通过。
13. `mvn -q -pl rag-backend -DskipTests compile` 已通过。
14. `mvn -q -pl rag-backend -Dtest=AgentActionExecutionTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test` 已通过。
15. `mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test` 已通过。
16. `mvn -q -pl rag-backend -DskipTests compile` 已通过。
17. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py` 已通过。
18. `mvn -q -pl rag-backend -Dtest=AgentActionExecutionTest,AgentRunServiceTest,AgentControllerTest,AgentRunScenarioTest test` 已通过。
19. `mvn -q -pl rag-backend -Dtest=EmbeddingRebuildServiceTest,AgentToolRegistryTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest test` 已通过。
20. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py` 已通过。
21. `mvn -q -pl rag-backend -DskipTests compile` 已通过。
22. `cd rag-frontend && npm run build` 已通过。
23. `cd rag-frontend && npm run build` 已通过。
24. `cd rag-frontend && npm run build` 已通过。
25. `mvn -q -pl rag-backend -Dtest=QaRetrieveProbeAgentToolTest,AgentToolRegistryTest test` 已通过。
26. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过。
27. `mvn -q -pl rag-backend -Dtest=AgentToolRegistryTest,SystemHealthAgentToolTest,QaReadinessAgentToolTest,DocumentsStatusAgentToolTest,IndexingTasksScanAgentToolTest,QaRetrieveProbeAgentToolTest test` 已通过。
28. `mvn -q -pl rag-backend -Dtest=AgentRunScenarioTest,AgentRunServiceTest,AgentControllerTest test` 已通过。
29. `./.venv/bin/python -m pytest rag-ai-service/tests/test_app.py rag-ai-service/tests/test_agent_runtime.py` 已通过。
30. `mvn -q -pl rag-backend -Dtest=AgentInternalToolControllerTest,QaRetrieveProbeAgentToolTest,AgentToolRegistryTest test` 已通过。
31. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过。
32. `./.venv/bin/python -m py_compile rag-ai-service/app/agent/tools.py` 已通过。
33. `GET http://127.0.0.1:8080/api/health` 已返回整体 `UP`。
34. `POST http://127.0.0.1:8080/api/knowledge-bases/finance-kb/agent/runs` 已通过真实链路返回 `WAITING_CONFIRMATION`。
35. `GET http://127.0.0.1:8080/api/knowledge-bases/finance-kb/agent/runs/AR-325284142981976065` 已确认 run/steps/actions 可从 Java 持久化状态读取。
36. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过，覆盖早期诊断图和智能 Tool-use Agent 骨架。
37. `mvn -q -pl rag-backend -Dtest=AgentInternalToolControllerTest,AgentToolRegistryTest,SystemHealthAgentToolTest,QaReadinessAgentToolTest test` 已通过，覆盖 Tool Definition v2 查询接口。
38. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过，覆盖 Day 18 决策校验和失败恢复。
39. `mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentRunScenarioTest test` 已通过，覆盖 Day 19 智能模式 recommended action 落库边界。
40. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 已通过，覆盖 Day 20 fake MCP / 只读 CLI 配置化 MVP。
41. `./.venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py rag-ai-service/tests/test_app.py` 已通过，覆盖 Python Runtime 主路径。
42. `mvn -q -pl rag-backend -Dtest=AgentRunServiceTest,AgentRunScenarioTest,AgentInternalToolControllerTest test` 已通过，覆盖后端 run/action/internal tools。
43. `cd rag-frontend && npm run build` 已通过，覆盖前端智能模式类型和构建。
44. `GET http://127.0.0.1:8001/health` 已返回 `UP`。
45. `GET http://127.0.0.1:8080/api/health` 已返回 `UP`，其中 PostgreSQL/Redis/AI Gateway/embedding/llm 均为 `UP`。
46. `GET http://127.0.0.1:5173/` 已返回前端 HTML。
47. `GET http://127.0.0.1:5173/api/knowledge-bases?pageNo=1&pageSize=1` 已通过 Vite proxy 返回 Java API 数据。
48. `POST http://127.0.0.1:5173/api/knowledge-bases/day20-cn-kb/agent/runs` 已创建 Agent run `AR-327301374603825153`，状态 `SUCCEEDED`，timeline 包含 `mcp.repo.status.inspect` 和 `cli.git.status`。

## 恢复入口

下次继续开发时按这个顺序恢复：

1. 先读本文件。
2. 再读 [plan.md](./plan.md)。
3. 如需理解长期设计取舍，读 [RFC-0012](../../rfcs/RFC-0012-langgraph-rag-ops-agent.md)。
