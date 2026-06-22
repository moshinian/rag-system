# Day 13：`qa.retrieve.probe` Dense / Hybrid 对比计划

## 目标

对齐 `plan.md` 的 Day 13：补齐 `qa.retrieve.probe` 简化版，对同一个问题分别执行 Dense / Hybrid 检索，返回可诊断的对比结果。

Day 13 的重点不是重做检索链路，而是把现有 Java 检索能力封装为 Agent 只读工具，让 Agent 能判断“检索结果为空、keyword 零命中、Hybrid 没有增益”等问题。

## 当前输入

- 现有 QA 检索接口已支持 Dense / Hybrid：
  - `POST /api/knowledge-bases/{kbCode}/qa/retrieve`
- 现有 Java 服务入口：
  - `QuestionAnsweringService.retrieve(kbCode, question, topK, retrievalMode)`
- 现有返回对象 `QuestionRetrievalResponse` 已包含：
  - `retrievalMode`
  - `fusionStrategy`
  - `denseHitCount`
  - `keywordHitCount`
  - `hitCount`
  - `denseDurationMs`
  - `keywordDurationMs`
  - `fusionDurationMs`
  - `totalDurationMs`
  - `chunks`
- Day 10-12 的前端 Agent 页面已经能展示 step 的 `outputJson`，Day 13 可以先复用现有 step JSON 展示，不强制新增复杂 UI。

## 边界约束

- `qa.retrieve.probe` 是只读工具。
- Java 继续是工具执行权威和业务规则中心。
- Python 只在 LangGraph Runtime 中调用工具、观察结果、生成诊断 summary。
- Python 不直接访问数据库。
- Python 不生成 `runCode / stepCode / actionCode`。
- Day 13 不新增写 action。
- Day 13 不改变 confirm/reject 机制。
- Day 13 不把 `qa.ask.probe` 纳入范围，不调用 LLM 问答链路。

## 工具定义

### 工具名

```text
qa.retrieve.probe
```

### 执行模式

```text
executionMode = READ_ONLY
maxRiskLevel = LOW
```

### 输入

```json
{
  "kbCode": "day20-cn-kb",
  "question": "第二百三十八条是什么",
  "topK": 5
}
```

规则：

- `kbCode` 从 `AgentToolContext` 获取，payload 中不信任外部覆盖。
- `question` 优先使用 Agent run 的 `question`。
- 如果 run 没有 question：
  - Day 13 可以跳过 `qa.retrieve.probe`，并记录 skipped step。
  - 不要自行编造问题。
- `topK` 默认 5，最大沿用现有 `/qa/retrieve` 的上限 10。

### 输出

建议输出结构：

```json
{
  "question": "第二百三十八条是什么",
  "topK": 5,
  "dense": {
    "hitCount": 5,
    "denseHitCount": 5,
    "keywordHitCount": 0,
    "fusionStrategy": "DENSE_ONLY",
    "totalDurationMs": 35,
    "sources": [
      {
        "documentCode": "DOC-xxx",
        "documentName": "民法典.pdf",
        "chunkId": 123,
        "chunkIndex": 10,
        "score": 0.82
      }
    ]
  },
  "hybrid": {
    "hitCount": 5,
    "denseHitCount": 8,
    "keywordHitCount": 2,
    "fusionStrategy": "RRF",
    "totalDurationMs": 48,
    "sources": []
  },
  "signals": {
    "denseEmpty": false,
    "hybridEmpty": false,
    "keywordZeroHit": false,
    "hybridNoGain": false,
    "topSourceChanged": true
  }
}
```

输出中只保留诊断需要的 source 摘要，不需要把完整 chunk content 塞进 Agent step。

## Java 实施计划

### 1. 新增 Agent 只读工具

建议文件：

- `rag-backend/src/main/java/com/example/rag/service/agent/QaRetrieveProbeAgentTool.java`

职责：

1. 从 `AgentToolContext` 获取 `kbCode`。
2. 从 input 中解析 `question / topK`。
3. 校验 question 非空。
4. 调用：
   - `QuestionAnsweringService.retrieve(kbCode, question, topK, RetrievalMode.DENSE)`
   - `QuestionAnsweringService.retrieve(kbCode, question, topK, RetrievalMode.HYBRID)`
5. 汇总 Dense / Hybrid 的命中数、耗时、TopK source 摘要。
6. 计算诊断 signals。
7. 返回 `AgentToolResult.success(output)`。

### 2. 注册工具

在现有 `AgentToolRegistry` 自动收集或配置中注册：

```text
qa.retrieve.probe
```

声明：

- `executionMode = READ_ONLY`
- `maxRiskLevel = LOW`

### 3. 诊断信号规则

Day 13 简化版只做确定性规则：

- `denseEmpty = dense.hitCount == 0`
- `hybridEmpty = hybrid.hitCount == 0`
- `keywordZeroHit = hybrid.keywordHitCount == 0`
- `hybridNoGain = hybrid.hitCount <= dense.hitCount && top source set 基本相同`
- `topSourceChanged = dense.top1 != hybrid.top1`

这些信号只用于诊断，不直接产生写 action。

### 4. Java 测试

建议新增或扩展：

- `QaRetrieveProbeAgentToolTest`
- `AgentToolRegistryTest`

测试重点：

1. 工具被注册，名字为 `qa.retrieve.probe`。
2. 工具是 `READ_ONLY / LOW`。
3. question 为空时返回失败或 skipped 语义，不执行检索。
4. Dense / Hybrid 都会被调用。
5. 输出包含 `dense / hybrid / signals`。
6. `keywordZeroHit / hybridNoGain / denseEmpty` 等信号可被稳定断言。

## Python 实施计划

### 1. 扩展工具客户端

文件：

- `rag-ai-service/app/agent/tools.py`

新增 Python 侧工具名：

```text
qa.retrieve.probe
```

Day 13 如果仍未接入 Java 真实工具 HTTP API，可以先在 `StaticAgentToolClient` 中返回固定结构，保证 Runtime 状态图和测试可跑。

但计划上应保持后续可替换：

- Python 不内置检索算法。
- Python 不直接访问 Java DB。
- Python 只调用工具客户端并读取返回结果。

### 2. 扩展 LangGraph 节点

文件：

- `rag-ai-service/app/agent/graph.py`

新增节点：

```text
qa_retrieve_probe
```

执行条件：

- run 有 `question`。
- goal 或 question 指向问答、检索、召回、答案找不到、结果不准等场景。

简化实现可以先采用：

- 只要 `question` 非空就执行 `qa.retrieve.probe`。
- 后续再把 `should_run_retrieve_probe` 做得更精细。

### 3. 扩展诊断 summary

根据 probe signals 补充诊断文本：

- Dense 和 Hybrid 都空：提示检索无命中，优先检查文档 chunk、embedding、问题表述。
- Dense 有命中但 Hybrid keyword 零命中：提示关键词分支未贡献结果。
- Hybrid 无增益：提示 Hybrid 与 Dense 结果高度相似，关键词召回收益有限。
- Hybrid top source 变化：提示 Hybrid 改变了排序，适合在前端/step JSON 中检查 source 差异。

Day 13 不新增推荐写 action。

### 4. Python 测试

建议扩展：

- `rag-ai-service/tests/test_agent_runtime.py`

测试重点：

1. 有 question 时 Runtime 包含 `qa_retrieve_probe` step。
2. 没有 question 时 probe step skipped 或不出现。
3. probe result signals 能进入 summary。
4. Runtime 仍不返回 `stepCode / actionCode`。
5. Runtime 仍不生成写 action。

## 前端实施计划

Day 13 前端先保持轻量。

### 1. 复用现有 step JSON

当前 Agent Workspace 已展示 step timeline，并可展开 `outputJson`。

Day 13 最小可接受：

- `qa_retrieve_probe` step 出现在 timeline。
- 展开 `outputJson` 可看到 Dense / Hybrid 对比结果。
- 无需新增独立 Dense/Hybrid 对比组件。

### 2. 可选增强

如果时间允许，再新增轻量展示：

- 在 timeline 中识别 `toolName=qa.retrieve.probe`。
- 将 `dense.hitCount / hybrid.hitCount / keywordHitCount / totalDurationMs` 摘要显示在 step 卡片里。
- 对 `signals.keywordZeroHit / hybridNoGain / denseEmpty` 用 tag 展示。

不建议 Day 13 大幅改 UI。

## 验收标准

### 功能验收

- Java 注册 `qa.retrieve.probe` 工具。
- 工具执行 Dense 和 Hybrid 两次检索。
- 工具输出包含：
  - Dense 命中数、耗时、TopK source 摘要。
  - Hybrid 命中数、keyword 命中数、耗时、TopK source 摘要。
  - `denseEmpty / hybridEmpty / keywordZeroHit / hybridNoGain / topSourceChanged` signals。
- Agent run 有 question 时，Runtime 会产生 `qa_retrieve_probe` step。
- Agent summary 能体现关键检索诊断信号。
- 不产生任何写 action。

### 边界验收

- `qa.retrieve.probe` 是 `READ_ONLY / LOW`。
- Python 不直接执行 Dense / Hybrid 检索逻辑。
- Python 不生成 `runCode / stepCode / actionCode`。
- 前端不新增任何写入口。
- confirm/reject 行为不受 Day 13 影响。

### 工程验收

- `mvn -q -pl rag-backend -Dtest=QaRetrieveProbeAgentToolTest,AgentToolRegistryTest test` 通过。
- `./venv/bin/python -m pytest rag-ai-service/tests/test_agent_runtime.py` 通过。
- 如有前端改动，`cd rag-frontend && npm run build` 通过。
- 完成后更新 `docs/work/rag-agent/current-status.md`，标记 Day 13 完成，并写清 Day 14 下一步。

## 建议执行顺序

1. 在 Java 新增 `QaRetrieveProbeAgentTool`。
2. 为 Java 工具补单元测试和注册测试。
3. 在 Python `tools.py` 增加 `qa.retrieve.probe` 工具调用形状。
4. 在 LangGraph 中接入 `qa_retrieve_probe` 节点。
5. 扩展 Python Runtime 测试。
6. 视情况做前端轻量展示增强。
7. 更新 `current-status.md`。

## Day 14 衔接

Day 13 完成后，进入 `plan.md` 的 Day 14：整理 README、架构图、接口说明、简历 bullet 和面试讲稿。

Day 14 应重点把已经完成的闭环讲清楚：

- Java 是业务权威和 Agent Run 状态中心。
- Python LangGraph 只做诊断编排。
- 前端展示轨迹、人审动作和执行结果。
- 两个核心写场景仍通过 human-in-the-loop 确认执行。
- `qa.retrieve.probe` 提供 Dense / Hybrid 检索诊断能力。
