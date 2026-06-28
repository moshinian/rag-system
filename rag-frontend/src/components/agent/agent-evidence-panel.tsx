import { Card, Empty, Space, Tag, Typography } from "antd";
import type { AgentEvidenceItem } from "./agent-run-view-model";

type AgentEvidencePanelProps = {
  evidence: AgentEvidenceItem[];
};

/** 展示稳定可解析的关键依据；不重复最终回答正文。 */
export function AgentEvidencePanel({ evidence }: AgentEvidencePanelProps) {
  if (evidence.length === 0) {
    return <Empty description="暂无稳定证据摘要" />;
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      {evidence.map((item) => (
        <Card key={item.id} size="small" title={item.title} extra={<Tag>{kindLabel(item.kind)}</Tag>}>
          <Typography.Paragraph style={{ marginBottom: 0 }}>{item.summary}</Typography.Paragraph>
        </Card>
      ))}
    </Space>
  );
}

function kindLabel(kind: AgentEvidenceItem["kind"]) {
  if (kind === "readiness") return "Readiness";
  if (kind === "retrieval_probe") return "Retrieval Probe";
  return "Retrieval Config";
}
