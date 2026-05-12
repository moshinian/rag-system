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

export function getDocument(kbCode: string, documentCode: string) {
  return apiClient.get<DocumentDetail>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}`
  );
}

export function disableDocument(kbCode: string, documentCode: string) {
  return apiClient.postJson<DocumentDetail>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/disable`
  );
}

export function enableDocument(kbCode: string, documentCode: string) {
  return apiClient.postJson<DocumentDetail>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/enable`
  );
}

export function listDocumentChunks(kbCode: string, documentCode: string) {
  return apiClient.get<DocumentChunk[]>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/chunks`
  );
}

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

export function listIndexingTasks(kbCode: string, documentCode: string) {
  return apiClient.get<DocumentIndexingTask[]>(
    `/api/knowledge-bases/${kbCode}/documents/${documentCode}/indexing-tasks`
  );
}

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

export function getReadiness(kbCode: string) {
  return apiClient.get<Readiness>(`/api/knowledge-bases/${kbCode}/qa/readiness`);
}
