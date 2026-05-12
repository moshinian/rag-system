import { useQuery } from "@tanstack/react-query";
import { Card, Collapse, Descriptions, Space, Table, Typography } from "antd";
import { useEffect, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { listQaHistory } from "../../api/qa";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { SourceList } from "../../components/source-viewer/source-list";
import { useCurrentKb } from "../../hooks/use-current-kb";
import { formatDateTime, truncateText } from "../../utils/format";
import {
  DEFAULT_PAGE,
  normalizePaginationParams,
  PAGE_SIZE_OPTIONS
} from "../../utils/pagination";

export function HistoryPage() {
  const kbCode = useCurrentKb();
  const [searchParams, setSearchParams] = useSearchParams();
  const { page, pageSize, normalized } = useMemo(
    () => normalizePaginationParams(searchParams),
    [searchParams]
  );

  useEffect(() => {
    if (
      searchParams.get("page") === normalized.page &&
      searchParams.get("pageSize") === normalized.pageSize
    ) {
      return;
    }

    setSearchParams(normalized, { replace: true });
  }, [normalized, searchParams, setSearchParams]);

  const query = useQuery({
    queryKey: ["qaHistory", kbCode, page, pageSize],
    queryFn: () => listQaHistory(kbCode!, page, pageSize),
    enabled: !!kbCode
  });
  const pagination = useMemo(
    () => ({
      current: page,
      pageSize,
      total: query.data?.total ?? 0,
      showSizeChanger: true,
      pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
      onChange: (nextPage: number, nextPageSize: number) => {
        setSearchParams(
          {
            page: String(nextPageSize !== pageSize ? DEFAULT_PAGE : nextPage),
            pageSize: String(nextPageSize)
          },
          { replace: false }
        );
      }
    }),
    [page, pageSize, query.data?.total, setSearchParams]
  );

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      {query.error ? <ApiErrorAlert error={query.error} /> : null}
      <Card title="问答记录" loading={query.isLoading}>
        <Table
          rowKey="messageCode"
          dataSource={query.data?.records ?? []}
          pagination={pagination}
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
