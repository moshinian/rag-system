export type DocumentStatus =
  | "UPLOADED"
  | "PARSING"
  | "PARSED"
  | "CHUNKING"
  | "INDEXED"
  | "FAILED"
  | "DISABLED";

export type EmbeddingStatus = "PENDING" | "EMBEDDING" | "EMBEDDED" | "FAILED";

export type IndexingTaskStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

export type IndexingTaskStage =
  | "QUEUED"
  | "DOCUMENT_PROCESSING"
  | "DOCUMENT_EMBEDDING"
  | "COMPLETED";

export type IndexingTaskTriggerSource = "SUBMIT" | "MANUAL_RETRY" | "RECOVERY";

export type DocumentSummary = {
  id: number;
  documentCode: string;
  knowledgeBaseCode: string;
  fileName: string;
  displayName: string;
  fileType: string;
  mediaType: string;
  fileSize: number;
  status: DocumentStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type DocumentDetail = DocumentSummary & {
  storagePath: string;
  contentHash: string;
  version: number;
  source?: string;
  tags?: string;
  errorMessage?: string;
};

export type DocumentUploadResponse = {
  id: number;
  documentCode: string;
  knowledgeBaseCode: string;
  fileName: string;
  displayName: string;
  fileType: string;
  mediaType: string;
  fileSize: number;
  storagePath: string;
  contentHash: string;
  status: DocumentStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type DocumentChunk = {
  id: number;
  documentId: number;
  chunkIndex: number;
  chunkType: string;
  title: string;
  content: string;
  contentLength: number;
  tokenCount: number;
  startOffset: number;
  endOffset: number;
  metadataJson?: string;
  status: string;
  embeddingStatus: EmbeddingStatus;
  embeddingModel?: string;
  embeddingUpdatedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type DocumentIndexingTask = {
  taskId: number;
  taskType: string;
  status: IndexingTaskStatus;
  taskStage: IndexingTaskStage;
  triggerSource: IndexingTaskTriggerSource;
  documentId: number;
  documentCode: string;
  knowledgeBaseCode: string;
  parentTaskId?: number;
  parserName?: string;
  chunkCount?: number;
  embeddedChunkCount?: number;
  retryCount?: number;
  maxRetryCount?: number;
  errorMessage?: string;
  createdBy: string;
  startedAt?: string;
  finishedAt?: string;
  lastHeartbeatAt?: string;
  recoveredAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type Readiness = {
  knowledgeBaseCode: string;
  knowledgeBaseStatus: string;
  questionAnsweringReady: boolean;
  embeddingProvider: string;
  embeddingModel: string;
  embeddingVectorDimensions: number;
  vectorStore: string;
  defaultTopK: number;
  indexedChunkCount: number;
  embeddedChunkCount: number;
  nextStep: string;
};

export type UploadDocumentPayload = {
  file: File;
  documentName?: string;
  tags?: string;
  source?: string;
  operator?: string;
};
