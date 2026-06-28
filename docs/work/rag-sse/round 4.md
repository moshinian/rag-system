# 第四轮：React Timeline UI

最后再做前端，这样问题不会和后端混在一起。

请执行 Agent SSE 改造第四批次。本轮只做 React 前端实时 Timeline UI。

## 本轮目标

1. 创建 Agent run 成功后立即连接 Java /events。
2. 使用 EventSource 订阅 SSE。
3. 展示 Agent Timeline。
4. 展示连接状态。
5. 收到 terminal 后关闭 EventSource，并调用 GET run detail 刷新最终状态。
6. 页面刷新或查看已有 run 时可以重新订阅并补发历史事件。

## 允许修改范围

允许修改：

* rag-frontend Agent 相关页面
* Agent run API client
* 新增 useAgentRunEvents hook
* 新增 AgentRunEvent 类型
* 前端构建配置如确有必要

不允许修改：

* Java 后端
* Python 后端
* confirm/reject 业务逻辑

## 关键要求

1. EventSource 只连接 Java，不连接 Python。
2. 按 eventId 去重。
3. terminal event 包括：

   * RUN_COMPLETED
   * RUN_FAILED
   * RUN_WAITING_CONFIRMATION
4. 收到 terminal 后：

   * 关闭 EventSource
   * 调用 GET run detail
   * 用数据库正式 run/steps/actions 刷新页面
5. 显示连接状态：

   * 已连接
   * 连接中断，正在重连
   * 已结束
6. confirm/reject 流程保持现状。

## 验收标准

1. npm run build 通过。
2. 创建 Agent run 后能实时看到事件出现。
3. 终态后 EventSource 关闭。
4. 页面刷新后能查看历史事件或最终状态。
5. 前端不会频繁轮询后端。

## 实施结果（2026-06-24）

本轮已完成 React Timeline UI 接入，未修改 Java 后端和 Python ai-service。

### 已完成内容

1. 新增前端事件类型：
   - `AgentRunEventType`
   - `AgentRunEventConnectionStatus`
   - `AgentRunEvent`
2. 新增 `useAgentRunEvents` hook：
   - 使用 `EventSource` 连接 Java `/api/knowledge-bases/{kbCode}/agent/runs/{runCode}/events`
   - 不直连 Python
   - 按 `eventId` 去重
   - 连接中断时展示“连接中断，正在重连”
   - 收到 `RUN_COMPLETED`、`RUN_FAILED`、`RUN_WAITING_CONFIRMATION` 后主动关闭 EventSource
3. 新增实时 Timeline 组件：
   - 展示事件类型、节点、工具、状态、消息、数据库顺序和 payload
   - 展示连接状态：未连接 / 连接中 / 已连接 / 连接中断，正在重连 / 已结束
4. Agent 页面接入：
   - 创建 run 成功后立即订阅该 run 的 SSE
   - 查询已有 run 后也会订阅事件流，用于历史补发
   - terminal 后调用 `GET /api/knowledge-bases/{kbCode}/agent/runs/{runCode}` 刷新正式 run/steps/actions
   - confirm/reject 流程保持原状

### 修改文件

- `rag-frontend/src/types/agent.ts`
- `rag-frontend/src/hooks/use-agent-run-events.ts`
- `rag-frontend/src/components/agent/agent-run-event-timeline.tsx`
- `rag-frontend/src/pages/agent/index.tsx`

### 验证结果

已执行：

```bash
cd rag-frontend
npm run build
```

结果：TypeScript 编译和 Vite production build 均通过。

### 后续留到 Round 5

- 三端联调验证前端实际 EventSource 展示效果。
- 验证浏览器自动重连时 `Last-Event-ID` 与 Java 历史补发的完整链路。
- 如需要，再补充前端组件测试或 Playwright 级 E2E。
