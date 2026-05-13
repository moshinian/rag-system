import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Card, Select, Space, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { disableDocument, enableDocument, listDocuments } from "../../api/document";
import { DocumentTable } from "../../components/tables/document-table";
import { ApiErrorAlert } from "../../components/feedback/api-error-alert";
import { useCurrentKb } from "../../hooks/use-current-kb";
import {
  DEFAULT_PAGE,
  normalizePaginationParams,
  PAGE_SIZE_OPTIONS
} from "../../utils/pagination";

/** 渲染页面内容。 */
export function DocumentsPage() {
  const { message } = App.useApp();

  const kbCode = useCurrentKb();

  const queryClient = useQueryClient();

  const [searchParams, setSearchParams] = useSearchParams();

  const [status, setStatus] = useState<string>();

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
    queryKey: ["documents", kbCode, status, page, pageSize],
    queryFn: () => listDocuments(kbCode!, { status, pageNo: page, pageSize }),
    enabled: !!kbCode
  });

  const [togglingDocumentCode, setTogglingDocumentCode] = useState<string>();

  /** 渲染页面内容。 */
  const refreshDocumentQueries = (documentCode: string) => {
    queryClient.invalidateQueries({ queryKey: ["documents", kbCode] });
    queryClient.invalidateQueries({ queryKey: ["documentDetail", kbCode, documentCode] });
    queryClient.invalidateQueries({ queryKey: ["documentChunks", kbCode, documentCode] });
    queryClient.invalidateQueries({ queryKey: ["readiness", kbCode] });
  };

  const disableMutation = useMutation({
    mutationFn: (documentCode: string) => {
      setTogglingDocumentCode(documentCode);
      return disableDocument(kbCode!, documentCode);
    },
    onSuccess: (_, documentCode) => {
      refreshDocumentQueries(documentCode);
      message.success("文档已禁用，历史 chunk 和向量已从检索口径中移除。");
    },
    onSettled: () => setTogglingDocumentCode(undefined)
  });

  const enableMutation = useMutation({
    mutationFn: (documentCode: string) => {
      setTogglingDocumentCode(documentCode);
      return enableDocument(kbCode!, documentCode);
    },
    onSuccess: (_, documentCode) => {
      refreshDocumentQueries(documentCode);
      message.success("文档已恢复，可重新参与检索口径统计。");
    },
    onSettled: () => setTogglingDocumentCode(undefined)
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
            onChange={(value) => {
              setStatus(value);
              setSearchParams(
                {
                  page: String(DEFAULT_PAGE),
                  pageSize: String(pageSize)
                },
                { replace: false }
              );
            }}
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
        <DocumentTable
          kbCode={kbCode!}
          data={query.data?.records ?? []}
          pagination={pagination}
          togglingDocumentCode={togglingDocumentCode}
          onDisable={(documentCode) => disableMutation.mutate(documentCode)}
          onEnable={(documentCode) => enableMutation.mutate(documentCode)}
        />
      </Card>
    </Space>
  );
}
