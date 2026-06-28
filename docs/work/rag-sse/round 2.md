# 第二轮：做 Python streaming，但 Java 先不切换

第一轮稳定后，再让 Codex 做 Python /v1/agent/runs/stream。这一轮重点是 curl Python SSE 能看到 step event。

请执行 Agent SSE 改造第二批次。本轮只做 Python streaming runtime，不切换 Java 到 streaming client，不改 React。

## 本轮目标

在 rag-ai-service 中新增：

1. AgentRuntimeEvent 模型。
2. AgentEventSink / QueueAgentEventSink。
3. traced_node 包装器。
4. LangGraph node 层事件发布。
5. AgentRuntime.stream_sse()。
6. POST /v1/agent/runs/stream 接口，返回 text/event-stream。
7. 保留旧 /v1/agent/runs JSON 接口完全兼容。

## 允许修改范围

允许修改：

* rag-ai-service/app/agent 相关代码
* rag-ai-service/app/api/routes.py
* Python 侧测试

不允许修改：

* Java 后端
* React 前端
* 旧 /v1/agent/runs 行为
* LLM planner 改成 token streaming

## 关键要求

1. Python 对 LLM 的调用仍保持同步，不做 LLM token streaming。
2. 每次 LangGraph node 实际执行时生成 nodeInvocationId，例如 AR-xxx-N-000001。
3. 同一次 node 的 STEP_STARTED / STEP_COMPLETED / STEP_FAILED / PLANNER_DECISION / TOOL_CALL_* / OBSERVATION_CREATED / ACTION_RECOMMENDED 共享同一个 nodeInvocationId。
4. 循环图中每次 llm_plan、execute_readonly_tool 都要生成不同 nodeInvocationId。
5. 不输出 chain-of-thought、完整 prompt、messages、API key、provider metadata。
6. stream_sse 必须保证每条 stream 恰好一个 terminal event：

   * RUN_COMPLETED
   * RUN_FAILED
7. final_report / fail_report 节点不要直接发送 terminal，由 AgentRuntime.stream_sse 外层统一判断并发送 terminal。
8. Queue 等待超过 10 秒时输出 SSE comment heartbeat：

   * : heartbeat
9. heartbeat 不进入业务事件。
10. 需要考虑 client disconnect，可用 cancellation flag。第一版不要求强制中断正在阻塞的 LLM/tool 调用，但断开后应停止后续 emit，并让后续 node 尽快终止。

## 验收标准

完成后请确保：

1. 旧 /v1/agent/runs JSON 接口仍能正常返回 AgentRuntimeResponse。
2. 新 /v1/agent/runs/stream 可以用 curl -N 看到 SSE event。
3. 每条 stream 有且只有一个 terminal event。
4. nodeInvocationId 在同一 node 执行内一致，在循环 node 多次执行时不同。
5. Python tests 通过，或者说明不能通过的具体原因。

## 验证命令

请至少提供：

curl -N -X POST http://127.0.0.1:8001/v1/agent/runs/stream 
-H "Content-Type: application/json" 
-H "Accept: text/event-stream" 
-d '...'

## 完成后请停止

不要修改 Java，不要接入 AgentRuntimeStreamingClient，不要改 React。

## 实施结果（2026-06-24）

本轮已完成：

1. 新增 Python `AgentRuntimeEvent`，统一使用 camelCase JSON。
2. 新增：
   - `AgentEventSink`
   - `QueueAgentEventSink`
   - `RuntimeEventSequence`
   - `traced_node`
   - SSE formatter
3. 固定诊断图和智能 Tool-use 图的全部 node 均已接入 traced wrapper。
4. 每次 node 调用生成独立 `nodeInvocationId`；同一次 node 内的 step、planner、tool、observation、action 事件共享该 ID。
5. 关键业务事件已接入：
   - `PLANNER_DECISION`
   - `TOOL_CALL_STARTED / COMPLETED / FAILED`
   - `OBSERVATION_CREATED`
   - `ACTION_RECOMMENDED`
6. 新增 `AgentRuntime.stream_sse()`：
   - 后台 daemon thread 执行同步 LangGraph
   - Queue 向 generator 传递事件
   - 10 秒无事件时输出 `: heartbeat`
   - `terminal_emitted` 保证正常 stream 只有一个 terminal
7. 新增 `POST /v1/agent/runs/stream`。
8. generator 关闭时设置 cancellation flag：
   - sink 停止接受后续事件
   - 后续 node 开始前协作终止
   - 不强制中断正在阻塞的 LLM/tool 调用
9. 旧 `/v1/agent/runs` 保持同步 JSON 响应，未改为 token streaming。
10. 推荐动作 catalog 的 input schema 已和现有 action payload 对齐，保留旧智能接口兼容。

本轮验证：

1. `./.venv/bin/python -m pytest -q rag-ai-service/tests`
   - `42 passed, 1 skipped`
2. `python -m compileall` 通过。
3. 真实 `curl -N /v1/agent/runs/stream` 返回 32 条事件：
   - 第一条 `RUN_STARTED`
   - node step/tool/observation 按执行过程流式返回
   - 最后一条且唯一 terminal 为 `RUN_COMPLETED`
4. 所有固定图 node 的 started/completed 事件均共享各自 correlation ID。
5. 旧 JSON 接口真实调用返回：
   - `status=SUCCEEDED`
   - 9 条 steps
   - 不包含 `stepCode/actionCode`
6. 事件检查未发现 prompt、messages、API key 或 provider metadata。

本轮未做：

1. Java `AgentRuntimeStreamingClient`
2. Java streaming event applier
3. Java 从旧 JSON client 切换到 Python SSE
4. React Timeline
