import { Tag } from "antd";
import {
  getDocumentStatusMeta,
  getEmbeddingStatusMeta,
  getKnowledgeBaseStatusMeta,
  getTaskStatusMeta
} from "../../utils/status";
import type {
  DocumentStatus,
  EmbeddingStatus,
  IndexingTaskStage,
  IndexingTaskStatus
} from "../../types/document";

type StatusBadgeProps =
  | { type: "knowledgeBase"; status: string }
  | { type: "document"; status: DocumentStatus }
  | { type: "embedding"; status: EmbeddingStatus }
  | { type: "task"; status: IndexingTaskStatus; stage: IndexingTaskStage };

/** 渲染复用组件。 */
export function StatusBadge(props: StatusBadgeProps) {
  const meta =
    props.type === "knowledgeBase"
      ? getKnowledgeBaseStatusMeta(props.status)
      : props.type === "document"
        ? getDocumentStatusMeta(props.status)
        : props.type === "embedding"
          ? getEmbeddingStatusMeta(props.status)
          : getTaskStatusMeta(props.status, props.stage);

  return <Tag color={meta.color}>{meta.label}</Tag>;
}
