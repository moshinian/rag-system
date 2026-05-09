import { apiClient } from "./client";
import type { PageResponse } from "../types/api";
import type { CreateKnowledgeBasePayload, KnowledgeBase } from "../types/knowledge-base";

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

export function getKnowledgeBase(kbCode: string) {
  return apiClient.get<KnowledgeBase>(`/api/knowledge-bases/${kbCode}`);
}

export function createKnowledgeBase(payload: CreateKnowledgeBasePayload) {
  return apiClient.postJson<KnowledgeBase>("/api/knowledge-bases", payload);
}
