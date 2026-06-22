import { apiClient } from "./client";
import type {
  AgentActionConfirmPayload,
  AgentActionRejectPayload,
  AgentRun,
  AgentRunCreatePayload
} from "../types/agent";

/** 创建 Agent 诊断 run。 */
export function createAgentRun(kbCode: string, payload: AgentRunCreatePayload) {
  return apiClient.postJson<AgentRun>(`/api/knowledge-bases/${kbCode}/agent/runs`, payload);
}

/** 查询 Agent 诊断 run 详情。 */
export function getAgentRun(kbCode: string, runCode: string) {
  return apiClient.get<AgentRun>(`/api/knowledge-bases/${kbCode}/agent/runs/${runCode}`);
}

/** 确认执行 Agent 推荐动作。 */
export function confirmAgentAction(
  kbCode: string,
  runCode: string,
  actionCode: string,
  payload?: AgentActionConfirmPayload
) {
  return apiClient.postJson<AgentRun>(
    `/api/knowledge-bases/${kbCode}/agent/runs/${runCode}/actions/${actionCode}/confirm`,
    payload ?? {}
  );
}

/** 拒绝 Agent 推荐动作。 */
export function rejectAgentAction(
  kbCode: string,
  runCode: string,
  actionCode: string,
  payload?: AgentActionRejectPayload
) {
  return apiClient.postJson<AgentRun>(
    `/api/knowledge-bases/${kbCode}/agent/runs/${runCode}/actions/${actionCode}/reject`,
    payload ?? {}
  );
}
