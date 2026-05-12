import { useEffect, useMemo, useState } from "react";
import { Card, Descriptions, Drawer, Empty, Grid, List, Space, Typography } from "antd";
import type { DocumentChunk } from "../../types/document";
import { truncateText } from "../../utils/format";
import { StatusBadge } from "../status/status-badge";

const { useBreakpoint } = Grid;

const DESKTOP_INSPECTOR_HEIGHT = 560;
const MOBILE_LIST_HEIGHT = 420;

type ChunkInspectorProps = {
  chunks: DocumentChunk[];
};

function formatMetadata(metadataJson?: string) {
  if (!metadataJson) {
    return undefined;
  }

  try {
    return JSON.stringify(JSON.parse(metadataJson), null, 2);
  } catch {
    return metadataJson;
  }
}

function renderChunkDetails(chunk?: DocumentChunk) {
  if (!chunk) {
    return <Empty description="请选择一个 Chunk 查看详情" />;
  }

  const metadata = formatMetadata(chunk.metadataJson);

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Chunk #{chunk.chunkIndex}
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 8 }}>
          {chunk.title || "未命名 Chunk"}
        </Typography.Paragraph>
      </div>

      <Descriptions size="small" column={2}>
        <Descriptions.Item label="Embedding">
          <StatusBadge type="embedding" status={chunk.embeddingStatus} />
        </Descriptions.Item>
        <Descriptions.Item label="Chunk 类型">{chunk.chunkType}</Descriptions.Item>
        <Descriptions.Item label="Offset">
          {chunk.startOffset} - {chunk.endOffset}
        </Descriptions.Item>
        <Descriptions.Item label="Token 估算">{chunk.tokenCount}</Descriptions.Item>
        <Descriptions.Item label="内容长度">{chunk.contentLength}</Descriptions.Item>
        <Descriptions.Item label="更新时间">{chunk.updatedAt}</Descriptions.Item>
      </Descriptions>

      <div>
        <Typography.Title level={5}>正文</Typography.Title>
        <Typography.Paragraph
          style={{
            whiteSpace: "pre-wrap",
            marginBottom: 0,
            padding: 16,
            borderRadius: 12,
            background: "rgba(23, 32, 51, 0.04)"
          }}
        >
          {chunk.content}
        </Typography.Paragraph>
      </div>

      {metadata ? (
        <div>
          <Typography.Title level={5}>Metadata</Typography.Title>
          <Typography.Paragraph
            style={{
              whiteSpace: "pre-wrap",
              marginBottom: 0,
              padding: 16,
              borderRadius: 12,
              background: "rgba(23, 32, 51, 0.04)",
              fontFamily: "SFMono-Regular, Consolas, monospace",
              fontSize: 13
            }}
          >
            {metadata}
          </Typography.Paragraph>
        </div>
      ) : null}

      <div>
        <Typography.Title level={5}>Future Retrieval Signals</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          这里为后续的 similarity score、rerank、citation trace、overlap 和 prompt grounding 保留位置。
        </Typography.Paragraph>
      </div>
    </Space>
  );
}

export function ChunkInspector({ chunks }: ChunkInspectorProps) {
  const screens = useBreakpoint();
  const isDesktop = !!screens.lg;
  const [selectedChunkIndex, setSelectedChunkIndex] = useState<number | undefined>(
    chunks[0]?.chunkIndex
  );
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    if (chunks.length === 0) {
      setSelectedChunkIndex(undefined);
      setDrawerOpen(false);
      return;
    }

    const stillExists = chunks.some((chunk) => chunk.chunkIndex === selectedChunkIndex);
    if (!stillExists) {
      setSelectedChunkIndex(chunks[0].chunkIndex);
    }
  }, [chunks, selectedChunkIndex]);

  useEffect(() => {
    if (isDesktop) {
      setDrawerOpen(false);
    }
  }, [isDesktop]);

  const selectedChunk = useMemo(
    () => chunks.find((chunk) => chunk.chunkIndex === selectedChunkIndex) ?? chunks[0],
    [chunks, selectedChunkIndex]
  );

  const handleSelect = (chunkIndex: number) => {
    setSelectedChunkIndex(chunkIndex);
    if (!isDesktop) {
      setDrawerOpen(true);
    }
  };

  const listContent = (
    <List
      dataSource={chunks}
      rowKey={(chunk) => String(chunk.chunkIndex)}
      split={false}
      renderItem={(chunk) => {
        const selected = chunk.chunkIndex === selectedChunk?.chunkIndex;

        return (
          <List.Item key={chunk.chunkIndex} style={{ padding: 0, border: "none", marginBottom: 12 }}>
            <button
              type="button"
              onClick={() => handleSelect(chunk.chunkIndex)}
              style={{
                width: "100%",
                textAlign: "left",
                background: selected ? "rgba(31, 111, 235, 0.08)" : "#fff",
                border: selected
                  ? "1px solid rgba(31, 111, 235, 0.32)"
                  : "1px solid rgba(23, 32, 51, 0.08)",
                borderRadius: 14,
                padding: 16,
                cursor: "pointer"
              }}
            >
              <Space direction="vertical" size={10} style={{ width: "100%" }}>
                <Space align="start" style={{ justifyContent: "space-between", width: "100%" }}>
                  <div>
                    <Typography.Text strong>Chunk #{chunk.chunkIndex}</Typography.Text>
                    <Typography.Paragraph type="secondary" style={{ margin: "4px 0 0" }}>
                      {chunk.title || "未命名 Chunk"}
                    </Typography.Paragraph>
                  </div>
                  <StatusBadge type="embedding" status={chunk.embeddingStatus} />
                </Space>

                <Space size="large" wrap>
                  <Typography.Text type="secondary">
                    Offset {chunk.startOffset} - {chunk.endOffset}
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    Token {chunk.tokenCount}
                  </Typography.Text>
                </Space>

                <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  {truncateText(chunk.content, 110)}
                </Typography.Paragraph>
              </Space>
            </button>
          </List.Item>
        );
      }}
    />
  );

  if (chunks.length === 0) {
    return (
      <Card title="Chunk Inspector (0)">
        <Empty description="当前文档还没有可检视的 Chunk。" />
      </Card>
    );
  }

  if (!isDesktop) {
    return (
      <Card title={`Chunk Inspector (${chunks.length})`}>
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            选择一条 Chunk，在 Inspector 中查看完整正文与调试上下文。
          </Typography.Paragraph>
          <div style={{ maxHeight: MOBILE_LIST_HEIGHT, overflowY: "auto", paddingRight: 4 }}>
            {listContent}
          </div>
        </Space>
        <Drawer
          title={selectedChunk ? `Chunk #${selectedChunk.chunkIndex}` : "Chunk Inspector"}
          placement="right"
          width="100%"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
        >
          {renderChunkDetails(selectedChunk)}
        </Drawer>
      </Card>
    );
  }

  return (
    <Card title={`Chunk Inspector (${chunks.length})`}>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(300px, 34%) minmax(0, 1fr)",
          gap: 16,
          alignItems: "stretch"
        }}
      >
        <div
          style={{
            height: DESKTOP_INSPECTOR_HEIGHT,
            overflowY: "auto",
            paddingRight: 4
          }}
        >
          <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              左侧是可滚动的 Chunk 摘要列表，右侧 Inspector 只聚焦当前选中的一条证据。
            </Typography.Paragraph>
            {listContent}
          </Space>
        </div>

        <div
          style={{
            height: DESKTOP_INSPECTOR_HEIGHT,
            overflowY: "auto",
            border: "1px solid rgba(23, 32, 51, 0.08)",
            borderRadius: 16,
            padding: 20,
            background: "rgba(255, 255, 255, 0.72)"
          }}
        >
          {renderChunkDetails(selectedChunk)}
        </div>
      </div>
    </Card>
  );
}
