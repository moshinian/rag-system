import { apiClient } from "./client";
import type { PageResponse } from "../types/api";
import type {
  CreateKnowledgeBasePayload,
  EmbeddingRebuildSubmitResponse,
  KnowledgeBase,
  KnowledgeBaseEnableResponse
} from "../types/knowledge-base";

/** 查询知识库分页列表。 */
export function listKnowledgeBases(params?: {
  status?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  const search = new URLSearchParams();
  if (params?.status) search.set("status", params.status);
  if (params?.pageNo) search.set("pageNo", String(params.pageNo));
  if (params?.pageSize) search.set("pageSize", String(params.pageSize));

  const suffix = search.toString() ? `?${search.toString()}` : "";
  return apiClient.get<PageResponse<KnowledgeBase>>(`/api/knowledge-bases${suffix}`);
}

/** 查询知识库详情。 */
export function getKnowledgeBase(kbCode: string) {
  return apiClient.get<KnowledgeBase>(`/api/knowledge-bases/${kbCode}`);
}

/** 调用创建知识库接口。 */
export function createKnowledgeBase(payload: CreateKnowledgeBasePayload) {
  return apiClient.postJson<KnowledgeBase>("/api/knowledge-bases", payload);
}

/** 调用禁用知识库接口。 */
export function disableKnowledgeBase(kbCode: string) {
  return apiClient.postJson<KnowledgeBase>(`/api/knowledge-bases/${kbCode}/disable`);
}

/** 调用启用知识库接口。 */
export function enableKnowledgeBase(
  kbCode: string,
  params?: { retryFailedIndexingTasks?: boolean; operator?: string }
) {
  const search = new URLSearchParams();
  if (params?.retryFailedIndexingTasks) search.set("retryFailedIndexingTasks", "true");
  if (params?.operator) search.set("operator", params.operator);

  const suffix = search.toString() ? `?${search.toString()}` : "";
  return apiClient.postJson<KnowledgeBaseEnableResponse>(
    `/api/knowledge-bases/${kbCode}/enable${suffix}`
  );
}

/** 提交全量重嵌入任务。 */
export function submitEmbeddingRebuild(operator?: string) {
  const suffix = operator ? `?operator=${encodeURIComponent(operator)}` : "";
  return apiClient.postJson<EmbeddingRebuildSubmitResponse>(`/api/admin/embeddings/rebuild${suffix}`);
}

/** 调用删除知识库接口。 */
export function deleteKnowledgeBase(kbCode: string) {
  return apiClient.delete<KnowledgeBase>(`/api/knowledge-bases/${kbCode}`);
}
