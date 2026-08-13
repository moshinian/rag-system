import { Card, List, Space, Typography } from "antd";
import type { RerankStatus, RetrievalMode, RetrievedChunk } from "../../types/qa";
import { formatFusionStrategy, formatRerankStatus, formatRetrievalMode } from "../../utils/format";

type RetrievalResultListProps = {
  items: RetrievedChunk[];
  retrieval?: {
    retrievalMode: RetrievalMode;
    fusionStrategy?: string;
    denseHitCount?: number;
    keywordHitCount?: number;
    hitCount?: number;
    denseDurationMs?: number;
    keywordDurationMs?: number;
    fusionDurationMs?: number;
    rerankStatus?: RerankStatus;
    rerankModel?: string | null;
    rerankCandidateCount?: number;
    rerankDurationMs?: number;
    totalDurationMs?: number;
  };
};

/** 渲染复用组件。 */
export function RetrievalResultList({ items, retrieval }: RetrievalResultListProps) {
  const subtitle = retrieval
    ? [
        formatRetrievalMode(retrieval.retrievalMode),
        typeof retrieval.denseHitCount === "number" ? `dense ${retrieval.denseHitCount}` : undefined,
        typeof retrieval.keywordHitCount === "number" ? `keyword ${retrieval.keywordHitCount}` : undefined,
        typeof retrieval.hitCount === "number" ? `final ${retrieval.hitCount}` : undefined,
        typeof retrieval.totalDurationMs === "number" ? `${retrieval.totalDurationMs} ms` : undefined,
        formatFusionStrategy(retrieval.fusionStrategy),
        retrieval.rerankStatus ? formatRerankStatus(retrieval.rerankStatus) : undefined,
        retrieval.rerankStatus === "APPLIED" && retrieval.rerankModel
          ? `${retrieval.rerankModel} ${retrieval.rerankCandidateCount ?? items.length}->${items.length}`
          : undefined,
        retrieval.rerankStatus === "APPLIED" && typeof retrieval.rerankDurationMs === "number"
          ? `rerank ${retrieval.rerankDurationMs} ms`
          : undefined
      ]
        .filter(Boolean)
        .join(" | ")
    : undefined;
  return (
    <Card title={`检索命中 (${items.length})`} extra={subtitle ? <Typography.Text type="secondary">{subtitle}</Typography.Text> : undefined}>
      <List
        dataSource={items}
        renderItem={(item) => (
          <List.Item>
            <Space direction="vertical" size={2} style={{ width: "100%" }}>
              <Space style={{ justifyContent: "space-between", width: "100%" }}>
                <Typography.Text strong>
                  {item.documentName} / Chunk #{item.chunkIndex}
                </Typography.Text>
                <Typography.Text type="secondary">
                  召回分 {item.score?.toFixed(4)}
                  {typeof item.rerankScore === "number" ? ` | 重排分 ${item.rerankScore.toFixed(4)}` : ""}
                </Typography.Text>
              </Space>
              <Typography.Text type="secondary">
                {item.documentCode} | offset {item.startOffset} - {item.endOffset}
              </Typography.Text>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}>
                {item.content}
              </Typography.Paragraph>
            </Space>
          </List.Item>
        )}
      />
    </Card>
  );
}
