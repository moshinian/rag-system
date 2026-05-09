import { useQuery } from "@tanstack/react-query";
import { Card, Select, Space, Typography } from "antd";
import { useState } from "react";
import { listDocuments } from "../../api/document";
import { DocumentTable } from "../../components/tables/document-table";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { useCurrentKb } from "../../hooks/use-current-kb";

export function DocumentsPage() {
  const kbCode = useCurrentKb();
  const [status, setStatus] = useState<string>();
  const query = useQuery({
    queryKey: ["documents", kbCode, status],
    queryFn: () => listDocuments(kbCode!, { status, pageNo: 1, pageSize: 100 }),
    enabled: !!kbCode
  });

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Card>
        <Space style={{ justifyContent: "space-between", width: "100%" }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            文档管理
          </Typography.Title>
          <Select
            allowClear
            placeholder="按状态筛选"
            style={{ width: 220 }}
            value={status}
            onChange={setStatus}
            options={[
              "UPLOADED",
              "PARSING",
              "PARSED",
              "CHUNKING",
              "INDEXED",
              "FAILED",
              "DISABLED"
            ].map((item) => ({ label: item, value: item }))}
          />
        </Space>
      </Card>
      {query.error ? <ApiErrorAlert error={query.error} /> : null}
      <Card loading={query.isLoading}>
        <DocumentTable kbCode={kbCode!} data={query.data?.records ?? []} />
      </Card>
    </Space>
  );
}
