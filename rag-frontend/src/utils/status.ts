import type {
  DocumentStatus,
  EmbeddingStatus,
  IndexingTaskStage,
  IndexingTaskStatus
} from "../types/document";

export function getKnowledgeBaseStatusMeta(status: string) {
  return status === "ACTIVE"
    ? { color: "success" as const, label: "可用" }
    : { color: "default" as const, label: "已停用" };
}

export function getDocumentStatusMeta(status: DocumentStatus) {
  const mapping: Record<DocumentStatus, { color: string; label: string }> = {
    UPLOADED: { color: "default", label: "已上传" },
    PARSING: { color: "processing", label: "解析中" },
    PARSED: { color: "cyan", label: "已解析" },
    CHUNKING: { color: "processing", label: "切块中" },
    INDEXED: { color: "success", label: "已完成" },
    FAILED: { color: "error", label: "失败" },
    DISABLED: { color: "default", label: "已禁用" }
  };
  return mapping[status];
}

export function getEmbeddingStatusMeta(status: EmbeddingStatus) {
  const mapping: Record<EmbeddingStatus, { color: string; label: string }> = {
    PENDING: { color: "default", label: "待向量化" },
    EMBEDDING: { color: "processing", label: "向量化中" },
    EMBEDDED: { color: "success", label: "已完成" },
    FAILED: { color: "error", label: "失败" }
  };
  return mapping[status];
}

export function getTaskStatusMeta(status: IndexingTaskStatus, stage: IndexingTaskStage) {
  if (status === "QUEUED") return { color: "default", label: "排队中" };
  if (status === "FAILED") return { color: "error", label: "失败" };
  if (status === "SUCCEEDED") return { color: "success", label: "已完成" };
  if (stage === "DOCUMENT_PROCESSING") return { color: "processing", label: "解析切块中" };
  if (stage === "DOCUMENT_EMBEDDING") return { color: "processing", label: "向量写库中" };
  return { color: "processing", label: "运行中" };
}

export function getTaskStageLabel(stage: IndexingTaskStage) {
  const mapping: Record<IndexingTaskStage, string> = {
    QUEUED: "排队",
    DOCUMENT_PROCESSING: "解析切块",
    DOCUMENT_EMBEDDING: "向量写库",
    COMPLETED: "完成"
  };
  return mapping[stage];
}
