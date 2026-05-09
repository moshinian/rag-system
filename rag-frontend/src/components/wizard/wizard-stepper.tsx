import { Card, Steps } from "antd";

type WizardStepperProps = {
  current: number;
};

const items = [
  { title: "知识库", description: "创建并启用知识库" },
  { title: "文档接入", description: "上传原始文档" },
  { title: "索引处理", description: "解析、切块、向量化" },
  { title: "检索问答", description: "召回、回答、看来源" }
];

export function WizardStepper({ current }: WizardStepperProps) {
  return (
    <Card>
      <Steps current={current} items={items} responsive />
    </Card>
  );
}
