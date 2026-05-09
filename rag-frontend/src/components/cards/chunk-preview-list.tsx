import { Card, Collapse, List, Space, Typography } from "antd";
import type { DocumentChunk } from "../../types/document";
import { StatusBadge } from "../status/status-badge";

type ChunkPreviewListProps = {
  chunks: DocumentChunk[];
};

export function ChunkPreviewList({ chunks }: ChunkPreviewListProps) {
  return (
    <Card title={`Chunk 预览 (${chunks.length})`}>
      <Collapse
        items={chunks.map((chunk) => ({
          key: String(chunk.id),
          label: (
            <Space>
              <Typography.Text strong>Chunk #{chunk.chunkIndex}</Typography.Text>
              <StatusBadge type="embedding" status={chunk.embeddingStatus} />
            </Space>
          ),
          children: (
            <List size="small">
              <List.Item>标题: {chunk.title}</List.Item>
              <List.Item>offset: {chunk.startOffset} - {chunk.endOffset}</List.Item>
              <List.Item>token 估算: {chunk.tokenCount}</List.Item>
              <List.Item style={{ whiteSpace: "pre-wrap" }}>{chunk.content}</List.Item>
            </List>
          )
        }))}
      />
    </Card>
  );
}
