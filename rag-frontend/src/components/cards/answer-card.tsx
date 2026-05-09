import { Card, Descriptions, Typography } from "antd";
import type { QaAnswerResponse } from "../../types/qa";

type AnswerCardProps = {
  answer: QaAnswerResponse;
};

export function AnswerCard({ answer }: AnswerCardProps) {
  return (
    <Card title="答案">
      <Descriptions size="small" column={2}>
        <Descriptions.Item label="模型">{answer.chatModel}</Descriptions.Item>
        <Descriptions.Item label="TopK">{answer.topK}</Descriptions.Item>
      </Descriptions>
      <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
        {answer.answer}
      </Typography.Paragraph>
    </Card>
  );
}
