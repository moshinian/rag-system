export type KnowledgeBaseStatus = "ACTIVE" | "INACTIVE";

export type KnowledgeBase = {
  id: number;
  kbCode: string;
  name: string;
  description?: string;
  status: KnowledgeBaseStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeBaseEnableResponse = KnowledgeBase & {
  retryFailedIndexingTasks: boolean;
  retriedFailedTaskCount: number;
  skippedDisabledDocumentCount: number;
  skippedActiveTaskDocumentCount: number;
  skippedRetryLimitDocumentCount: number;
  retriedDocumentCodes: string[];
};

export type EmbeddingRebuildSubmitResponse = {
  rebuildRunId: number;
  status: string;
  targetFingerprint: string;
  embeddingModel: string;
  embeddingProvider: string;
  vectorDimensions: number;
  distanceMetric: string;
  operator?: string;
  submittedAt: string;
};

export type CreateKnowledgeBasePayload = {
  kbCode: string;
  name: string;
  description?: string;
  createdBy?: string;
};
