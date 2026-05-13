import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Card, Form, Input, Space, Typography, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import type { UploadFile } from "antd";
import { useNavigate } from "react-router-dom";
import { submitIndexingTask, uploadDocument } from "../../api/document";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { WizardStepper } from "../../components/wizard/wizard-stepper";
import { useCurrentKb } from "../../hooks/use-current-kb";

type UploadForm = {
  file: UploadFile[];
  documentName?: string;
  tags?: string;
  source?: string;
  operator?: string;
};

/** 渲染页面内容。 */
export function UploadPage() {
  const kbCode = useCurrentKb();

  const navigate = useNavigate();

  const mutation = useMutation({
    mutationFn: async (values: UploadForm) => {
      const file = values.file?.[0]?.originFileObj;
      if (!file || !kbCode) {
        throw new Error("请选择文件和知识库");
      }

      const uploaded = await uploadDocument(kbCode, {
        file,
        documentName: values.documentName,
        tags: values.tags,
        source: values.source,
        operator: values.operator
      });
      await submitIndexingTask(kbCode, uploaded.documentCode, values.operator);
      return uploaded;
    },
    onSuccess: (uploaded) => navigate(`/kb/${kbCode}/documents/${uploaded.documentCode}`)
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <WizardStepper current={1} />
      <Card title="文档接入">
        <Alert
          type="info"
          showIcon
          message="上传成功后自动发起异步索引"
          description="主流程默认使用 upload -> index，不要求用户手动触发 process/embed。"
          style={{ marginBottom: 24 }}
        />
        <Form<UploadForm> layout="vertical" onFinish={(values) => mutation.mutate(values)}>
          <Form.Item
            name="file"
            label="上传文件"
            valuePropName="fileList"
            getValueFromEvent={(event) => event?.fileList}
            rules={[{ required: true, message: "请上传一个文件" }]}
          >
            <Upload.Dragger beforeUpload={() => false} maxCount={1} accept=".md,.txt,.pdf">
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <Typography.Text>支持 md / txt / pdf，最大 20MB</Typography.Text>
            </Upload.Dragger>
          </Form.Item>
          <Form.Item name="documentName" label="展示名称">
            <Input placeholder="不填则默认使用原始文件名" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Input placeholder="例如: 财务,结算,制度" />
          </Form.Item>
          <Form.Item name="source" label="来源">
            <Input placeholder="例如: 内部制度库" />
          </Form.Item>
          <Form.Item name="operator" label="操作人">
            <Input placeholder="frontend-user" />
          </Form.Item>
          {mutation.error ? <ApiErrorAlert error={mutation.error} /> : null}
          <Button type="primary" htmlType="submit" loading={mutation.isPending}>
            上传并开始索引
          </Button>
        </Form>
      </Card>
    </Space>
  );
}
