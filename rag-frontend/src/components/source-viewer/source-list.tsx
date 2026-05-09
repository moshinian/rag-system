import { Drawer, List, Space, Typography } from "antd";
import { useState } from "react";
import type { QaSource } from "../../types/qa";

type SourceListProps = {
  sources: QaSource[];
};

export function SourceList({ sources }: SourceListProps) {
  const [selected, setSelected] = useState<QaSource | null>(null);

  return (
    <>
      <List
        header={<Typography.Text strong>来源列表 ({sources.length})</Typography.Text>}
        dataSource={sources}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: "pointer" }}
            onClick={() => setSelected(item)}
          >
            <Space direction="vertical" size={2}>
              <Typography.Text strong>
                {item.documentName} / Chunk #{item.chunkIndex}
              </Typography.Text>
              <Typography.Text type="secondary">
                score: {item.score?.toFixed(4)} | offset {item.startOffset} - {item.endOffset}
              </Typography.Text>
            </Space>
          </List.Item>
        )}
      />
      <Drawer
        title={selected ? `${selected.documentName} / Chunk #${selected.chunkIndex}` : "来源详情"}
        open={!!selected}
        onClose={() => setSelected(null)}
        width={680}
      >
        <Typography.Paragraph style={{ whiteSpace: "pre-wrap" }}>
          {selected?.content}
        </Typography.Paragraph>
      </Drawer>
    </>
  );
}
