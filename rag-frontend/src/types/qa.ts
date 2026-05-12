import type { BackendLongId } from "./backend-id";

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
};

export type QaSource = {
  documentCode: string;
  documentName: string;
  chunkId: BackendLongId;
  chunkIndex: number;
  content: string;
  score: number;
  startOffset: number;
  endOffset: number;
};

export type RetrievalResponse = {
  knowledgeBaseCode: string;
  question: string;
  embeddingModel: string;
  topK: number;
  hitCount: number;
  chunks: RetrievedChunk[];
};

export type QaAnswerResponse = {
  question: string;
  answer: string;
  topK: number;
  chatModel: string;
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
  latencyMs?: number;
  promptTemplate?: string;
  retrievalResults: RetrievedChunk[];
  sources: QaSource[];
  createdAt: string;
};
