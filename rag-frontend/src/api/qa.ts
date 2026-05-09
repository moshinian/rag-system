import { apiClient } from "./client";
import type { PageResponse } from "../types/api";
import type { QaAnswerResponse, QaHistoryRecord, RetrievalResponse } from "../types/qa";

type AskPayload = {
  question: string;
  topK?: number;
};

export function retrieve(kbCode: string, payload: AskPayload) {
  return apiClient.postJson<RetrievalResponse>(
    `/api/knowledge-bases/${kbCode}/qa/retrieve`,
    payload
  );
}

export function ask(kbCode: string, payload: AskPayload) {
  return apiClient.postJson<QaAnswerResponse>(
    `/api/knowledge-bases/${kbCode}/qa/ask`,
    payload
  );
}

export function listQaHistory(kbCode: string, pageNo = 1, pageSize = 20) {
  return apiClient.get<PageResponse<QaHistoryRecord>>(
    `/api/knowledge-bases/${kbCode}/qa/history?pageNo=${pageNo}&pageSize=${pageSize}`
  );
}
