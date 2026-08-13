import type { BackendLongId } from "./backend-id";

export type RetrievalMode = "DENSE" | "HYBRID";
export type RerankStatus = "DISABLED" | "SKIPPED_EMPTY" | "APPLIED" | "DEGRADED";

export type RetrievedChunk = {
  chunkId: BackendLongId;
  documentId: BackendLongId;
  documentCode: string;
  documentName: string;
  chunkIndex: number;
  chunkType: string;
  content: string;
  startOffset: number;
  endOffset: number;
  embeddingModel?: string;
  score: number;
  rerankScore?: number | null;
};

export type QaSource = {
  documentCode: string;
  documentName: string;
  chunkId: BackendLongId;
  chunkIndex: number;
  content: string;
  score: number;
  rerankScore?: number | null;
  startOffset: number;
  endOffset: number;
};

export type RetrievalResponse = {
  knowledgeBaseCode: string;
  question: string;
  embeddingModel: string;
  topK: number;
  retrievalMode: RetrievalMode;
  fusionStrategy: string;
  denseHitCount: number;
  keywordHitCount: number;
  hitCount: number;
  denseDurationMs: number;
  keywordDurationMs: number;
  fusionDurationMs: number;
  rerankStatus: RerankStatus;
  rerankModel?: string | null;
  rerankCandidateCount: number;
  rerankDurationMs: number;
  totalDurationMs: number;
  chunks: RetrievedChunk[];
};

export type QaAnswerResponse = {
  question: string;
  answer: string;
  topK: number;
  chatModel: string;
  retrievalMode: RetrievalMode;
  fusionStrategy: string;
  denseHitCount: number;
  keywordHitCount: number;
  hitCount: number;
  denseDurationMs: number;
  keywordDurationMs: number;
  fusionDurationMs: number;
  rerankStatus: RerankStatus;
  rerankModel?: string | null;
  rerankCandidateCount: number;
  rerankDurationMs: number;
  llmDurationMs: number;
  totalDurationMs: number;
  retrievalResults: RetrievedChunk[];
  sources: QaSource[];
};

export type QaHistoryRecord = {
  sessionCode: string;
  sessionName: string;
  messageCode: string;
  question: string;
  answer: string;
  chatModel: string;
  topK: number;
  retrievalMode: RetrievalMode;
  fusionStrategy: string;
  rerankStatus: RerankStatus;
  rerankModel?: string | null;
  rerankCandidateCount: number;
  rerankDurationMs: number;
  latencyMs?: number;
  promptTemplate?: string;
  retrievalResults: RetrievedChunk[];
  sources: QaSource[];
  createdAt: string;
};
