import { apiClient } from "./client";
import type {
  DocumentChunk,
  DocumentDetail,
  DocumentIndexingTask,
  DocumentSummary,
  DocumentUploadResponse,
  Readiness,
  UploadDocumentPayload
} from "../types/document";
import type { PageResponse } from "../types/api";
import type { BackendLongId } from "../types/backend-id";
import { toBackendIdParam } from "../types/backend-id";

/** 查询文档分页列表。 */
export function listDocuments(
  kbCode: string,
  params?: { status?: string; pageNo?: number; pageSize?: number }
) {
  const search = new URLSearchParams();
  if (params?.status) search.set("status", params.status);
  if (params?.pageNo) search.set("pageNo", String(params.pageNo));
  if (params?.pageSize) search.set("pageSize", String(params.pageSize));

  const suffix = search.toString() ? `?${search.toString()}` : "";
  return apiClient.get<PageResponse<DocumentSummary>>(
    `/api/knowledge-bases/${kbCode}/documents${suffix}`
  );
}

/** 查询文档详情。 */
export function getDocument(kbCode: string, documentCode: string) {
  return apiClient.get<DocumentDetail>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}`
  );
}

/** 调用禁用文档接口。 */
export function disableDocument(kbCode: string, documentCode: string) {
  return apiClient.postJson<DocumentDetail>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/disable`
  );
}

/** 调用恢复文档接口。 */
export function enableDocument(kbCode: string, documentCode: string) {
  return apiClient.postJson<DocumentDetail>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/enable`
  );
}

/** 查询文档切块列表。 */
export function listDocumentChunks(kbCode: string, documentCode: string) {
  return apiClient.get<DocumentChunk[]>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/chunks`
  );
}

/** 上传文档文件。 */
export function uploadDocument(kbCode: string, payload: UploadDocumentPayload) {
  const formData = new FormData();
  formData.append("file", payload.file);
  if (payload.documentName) formData.append("documentName", payload.documentName);
  if (payload.tags) formData.append("tags", payload.tags);
  if (payload.source) formData.append("source", payload.source);
  if (payload.operator) formData.append("operator", payload.operator);
  return apiClient.postForm<DocumentUploadResponse>(
    `/api/knowledge-bases/${kbCode}/documents/upload`,
    formData
  );
}

/** 提交异步索引任务。 */
export function submitIndexingTask(
  kbCode: string,
  documentCode: string,
  operator?: string
) {
  const suffix = operator ? `?operator=${encodeURIComponent(operator)}` : "";
  return apiClient.postJson<DocumentIndexingTask>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/index${suffix}`
  );
}

/** 查询索引任务历史。 */
export function listIndexingTasks(kbCode: string, documentCode: string) {
  return apiClient.get<DocumentIndexingTask[]>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/indexing-tasks`
  );
}

/** 重试失败索引任务。 */
export function retryIndexingTask(
  kbCode: string,
  documentCode: string,
  taskId: BackendLongId,
  operator?: string
) {
  const suffix = operator ? `?operator=${encodeURIComponent(operator)}` : "";
  return apiClient.postJson<DocumentIndexingTask>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/indexing-tasks/${toBackendIdParam(taskId)}/retry${suffix}`
  );
}

/** 查询问答 readiness 状态。 */
export function getReadiness(kbCode: string) {
  return apiClient.get<Readiness>(`/api/knowledge-bases/${kbCode}/qa/readiness`);
}
