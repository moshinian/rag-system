import { useQuery } from "@tanstack/react-query";
import { Card, Collapse, Descriptions, Space, Table, Typography } from "antd";
import { listQaHistory } from "../../api/qa";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { SourceList } from "../../components/source-viewer/source-list";
import { useCurrentKb } from "../../hooks/use-current-kb";
import { formatDateTime, truncateText } from "../../utils/format";

export function HistoryPage() {
  const kbCode = useCurrentKb();
  const query = useQuery({
    queryKey: ["qaHistory", kbCode],
    queryFn: () => listQaHistory(kbCode!, 1, 50),
    enabled: !!kbCode
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      {query.error ? <ApiErrorAlert error={query.error} /> : null}
      <Card title="问答记录" loading={query.isLoading}>
        <Table
          rowKey="messageCode"
          dataSource={query.data?.records ?? []}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => (
              <Collapse
                items={[
                  {
                    key: "answer",
                    label: "回答与来源",
                    children: (
                      <Space direction="vertical" size="large" style={{ width: "100%" }}>
                        <Descriptions size="small" column={2}>
                          <Descriptions.Item label="模型">{record.chatModel}</Descriptions.Item>
                          <Descriptions.Item label="TopK">{record.topK}</Descriptions.Item>
                          <Descriptions.Item label="耗时">{record.latencyMs ?? "-"} ms</Descriptions.Item>
                          <Descriptions.Item label="创建时间">
                            {formatDateTime(record.createdAt)}
                          </Descriptions.Item>
                        </Descriptions>
                        <Typography.Paragraph style={{ whiteSpace: "pre-wrap" }}>
                          {record.answer}
                        </Typography.Paragraph>
                        <SourceList sources={record.sources} />
                      </Space>
                    )
                  }
                ]}
              />
            )
          }}
          columns={[
            {
              title: "问题",
              dataIndex: "question",
              render: (value: string) => truncateText(value, 48)
            },
            {
              title: "会话",
              dataIndex: "sessionName"
            },
            {
              title: "模型",
              dataIndex: "chatModel"
            },
            {
              title: "来源数",
              render: (_, record) => record.sources.length
            },
            {
              title: "时间",
              dataIndex: "createdAt",
              render: (value: string) => formatDateTime(value)
            }
          ]}
        />
      </Card>
    </Space>
  );
}
