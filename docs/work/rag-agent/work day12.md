# Day 12：前端 Confirm/Reject 与执行结果展示计划

## 目标

对齐 `plan.md` 的 Day 12：前端接入 Agent 推荐动作的 `confirm/reject`，并在执行后展示最新动作状态、执行结果或错误信息。

Day 12 只打通前端到 Java Agent Run API 的人审入口，不新增 Python 写能力，不绕过 Java 执行业务写操作。

## 当前输入

- Day 8 已完成 Java 后端动作确认/拒绝 API：
  - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/confirm`
  - `POST /api/knowledge-bases/{kbCode}/agent/runs/{runCode}/actions/{actionCode}/reject`
- Day 8 已支持 `document.indexing_task.retry` 的 Java 侧确认执行。
- Day 9 已支持 `embedding.rebuild.submit` 的 Java 侧确认执行。
- Day 10 已有 Agent Workspace 页面，可创建 run、查询 run、展示 overview/summary/steps/actions。
- Day 11 已有推荐动作卡片和执行轨迹展示，但动作卡片仍未接入确认/拒绝按钮。

## 边界约束

- 前端只能调用 Java Agent confirm/reject API。
- 前端不能直接调用 Python Runtime。
- 前端不能直接调用业务写 API，例如 indexing retry 或 embedding rebuild submit。
- 前端不生成 `runCode / stepCode / actionCode`。
- Python 仍只负责 LangGraph Agent Runtime，不执行 DB 写入。
- 实际写操作必须由 Java 在 confirm 后执行。
- 只有 `requiresConfirmation=true` 且 `status=PENDING_CONFIRMATION` 的 action 才展示可操作按钮。
- `HIGH` risk 动作前端默认不开放直接确认，只展示风险提示；后端仍是最终权限边界。

## 实施范围

### 1. 扩展前端 Agent API 类型

文件：

- `rag-frontend/src/types/agent.ts`
- `rag-frontend/src/api/agent.ts`

计划新增类型：

```ts
export type AgentActionConfirmPayload = {
  operator?: string;
};

export type AgentActionRejectPayload = {
  operator?: string;
  reason?: string;
};
```

计划新增 API：

```ts
export function confirmAgentAction(
  kbCode: string,
  runCode: string,
  actionCode: string,
  payload?: AgentActionConfirmPayload,
) {
  return apiClient.postJson<AgentRun>(
    `/api/knowledge-bases/${kbCode}/agent/runs/${runCode}/actions/${actionCode}/confirm`,
    payload ?? {},
  );
}

export function rejectAgentAction(
  kbCode: string,
  runCode: string,
  actionCode: string,
  payload?: AgentActionRejectPayload,
) {
  return apiClient.postJson<AgentRun>(
    `/api/knowledge-bases/${kbCode}/agent/runs/${runCode}/actions/${actionCode}/reject`,
    payload ?? {},
  );
}
```

### 2. Agent 页面接管 confirm/reject 状态

文件：

- `rag-frontend/src/pages/agent/index.tsx`

页面继续作为 `currentRun` 的状态中心，负责：

- 调用 `confirmAgentAction`。
- 调用 `rejectAgentAction`。
- 接收后端返回的最新 `AgentRun`。
- 用返回结果刷新 `currentRun`。
- 维护当前正在 confirm/reject 的 `actionCode`，避免重复点击。
- 通过现有错误展示机制显示 API 错误和 `requestId`。

建议页面向动作卡片传入：

```ts
<AgentActionCards
  actions={currentRun.actions}
  onConfirm={handleConfirmAction}
  onReject={handleRejectAction}
  confirmingActionCode={confirmingActionCode}
  rejectingActionCode={rejectingActionCode}
/>
```

### 3. 推荐动作卡片增加人审交互

文件：

- `rag-frontend/src/components/agent/agent-action-cards.tsx`

动作卡片负责展示和触发用户意图，不直接保存 run 状态。

计划增加：

- `confirm` 按钮：
  - 仅 `PENDING_CONFIRMATION + requiresConfirmation + riskLevel !== HIGH` 可点击。
  - 使用 `Popconfirm` 二次确认。
  - 点击后调用父组件 `onConfirm(action)`。
- `reject` 按钮：
  - 仅 `PENDING_CONFIRMATION + requiresConfirmation` 可点击。
  - 弹出 reason 输入框。
  - 点击提交后调用父组件 `onReject(action, reason)`。
- `HIGH` risk 提示：
  - 不展示直接确认能力，或展示 disabled confirm 按钮。
  - 卡片内用 `Alert` 提示需要更高等级人工流程。
- 非 pending 状态：
  - 不展示操作按钮。
  - 继续展示 `confirmedBy / confirmedAt / executedAt / resultJson / errorMessage`。

### 4. 执行结果展示

Day 11 的动作卡片已经具备 `resultJson` 和 `errorMessage` 展示基础，Day 12 需要保证 confirm/reject 后刷新到最新 run。

展示规则：

- `SUCCEEDED`：
  - 展示成功状态 tag。
  - 若存在 `resultJson`，保留 JSON 折叠面板。
- `FAILED`：
  - 展示失败状态 tag。
  - 展示 `errorMessage` alert。
  - 若存在 `resultJson`，仍允许展开查看。
- `REJECTED`：
  - 展示拒绝状态 tag。
  - 如后端返回拒绝原因或结果，展示在 result 区域。
- `EXECUTING`：
  - 展示执行中状态。
  - Day 12 不要求轮询，但 confirm/reject 返回后应至少刷新一次 run。

### 5. 不在 Day 12 做的事

- 不新增 Java 后端 confirm/reject API。
- 不新增 Python Runtime 写能力。
- 不实现 `qa.retrieve.probe`。
- 不重做 Agent Workspace 布局。
- 不改变 Agent Run/Step/Action 编码归属。
- 不绕开 Java 直接触发 indexing retry 或 embedding rebuild。

## 验收标准

### 功能验收

- `PENDING_CONFIRMATION` 且 `requiresConfirmation=true` 的动作卡片展示 confirm/reject。
- confirm 调用 Java confirm API，并用返回的 `AgentRun` 刷新页面。
- reject 调用 Java reject API，并用返回的 `AgentRun` 刷新页面。
- confirm/reject 期间按钮有 loading 或 disabled 状态，避免重复提交。
- API 错误能在页面展示，包含后端返回的 requestId。
- action 执行后的 `status / executedAt / resultJson / errorMessage` 能在卡片中看到。

### 场景验收

- `reembedRequired` 场景：
  - 推荐动作 `embedding.rebuild.submit` 可在前端确认。
  - 确认后由 Java 执行 embedding rebuild submit。
  - 页面刷新后能看到动作状态和执行结果。
- `FAILED indexing task` 场景：
  - 推荐动作 `document.indexing_task.retry` 可在前端确认或拒绝。
  - 确认后由 Java 执行 retry。
  - 拒绝后 action 状态变为 `REJECTED`，页面可见。

### 工程验收

- `npm run build` 通过。
- 前端没有 TypeScript 类型错误。
- 没有新增前端直连 Python 或业务写 API 的调用。
- 完成后更新 `docs/work/rag-agent/current-status.md`，标记 Day 12 完成，并写清 Day 13 下一步。

## 建议执行顺序

1. 扩展 `types/agent.ts` 和 `api/agent.ts`。
2. 在 `AgentPage` 中增加 confirm/reject handler 和 loading 状态。
3. 扩展 `AgentActionCards` props 和按钮交互。
4. 补齐 reject reason modal。
5. 运行 `npm run build`。
6. 用两个演示场景手工验证。
7. 更新 `current-status.md`。

## Day 13 衔接

Day 12 完成后，进入 `plan.md` 的 Day 13：实现 `qa.retrieve.probe` 的简化版，对同一个问题展示 Dense / Hybrid 的检索结果差异。

Day 13 仍应保持 Java 为业务权威：

- Python 只发起 tool 调用意图或读取 tool 结果。
- 检索探测 API 由 Java 提供。
- Agent Run 状态、Step、Action 仍由 Java 落库。
