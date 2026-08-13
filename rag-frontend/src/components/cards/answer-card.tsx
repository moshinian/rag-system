import { Card, Descriptions, Typography } from "antd";
import type { QaAnswerResponse } from "../../types/qa";
import { formatFusionStrategy, formatRerankStatus, formatRetrievalMode } from "../../utils/format";

type AnswerCardProps = {
  answer: QaAnswerResponse;
};

/** 渲染复用组件。 */
export function AnswerCard({ answer }: AnswerCardProps) {
  return (
    <Card title="答案">
      <Descriptions size="small" column={2}>
        <Descriptions.Item label="模型">{answer.chatModel}</Descriptions.Item>
        <Descriptions.Item label="TopK">{answer.topK}</Descriptions.Item>
        <Descriptions.Item label="检索模式">
          {formatRetrievalMode(answer.retrievalMode)}
        </Descriptions.Item>
        <Descriptions.Item label="融合策略">
          {formatFusionStrategy(answer.fusionStrategy)}
        </Descriptions.Item>
        <Descriptions.Item label="重排序">
          {formatRerankStatus(answer.rerankStatus)}
        </Descriptions.Item>
        <Descriptions.Item label="重排模型">
          {answer.rerankModel ?? "-"}
        </Descriptions.Item>
      </Descriptions>
      <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
        {answer.answer}
      </Typography.Paragraph>
    </Card>
  );
}
