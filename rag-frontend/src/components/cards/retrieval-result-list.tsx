import { Card, List, Space, Typography } from "antd";
import type { RetrievedChunk } from "../../types/qa";

type RetrievalResultListProps = {
  items: RetrievedChunk[];
};

/** 渲染复用组件。 */
export function RetrievalResultList({ items }: RetrievalResultListProps) {
  return (
    <Card title={`检索命中 (${items.length})`}>
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
                  score: {item.score?.toFixed(4)}
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
