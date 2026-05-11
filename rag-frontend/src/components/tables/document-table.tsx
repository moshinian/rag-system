import { Button, Popconfirm, Space, Table, Typography } from "antd";
import { Link } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import type { DocumentSummary } from "../../types/document";
import { formatDateTime, formatFileSize, truncateText } from "../../utils/format";
import { StatusBadge } from "../status/status-badge";

type DocumentTableProps = {
  kbCode: string;
  data: DocumentSummary[];
  togglingDocumentCode?: string;
  onDisable?: (documentCode: string) => void;
  onEnable?: (documentCode: string) => void;
};

export function DocumentTable({ kbCode, data, togglingDocumentCode, onDisable, onEnable }: DocumentTableProps) {
  const columns: ColumnsType<DocumentSummary> = [
    {
      title: "文档",
      dataIndex: "displayName",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.displayName}</Typography.Text>
          <Typography.Text type="secondary">
            {truncateText(record.fileName, 40)}
          </Typography.Text>
        </Space>
      )
    },
    {
      title: "类型",
      dataIndex: "fileType",
      width: 100
    },
    {
      title: "大小",
      dataIndex: "fileSize",
      width: 110,
      render: (value: number) => formatFileSize(value)
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 120,
      render: (value) => <StatusBadge type="document" status={value} />
    },
    {
      title: "更新时间",
      dataIndex: "updatedAt",
      width: 180,
      render: (value: string) => formatDateTime(value)
    },
    {
      title: "操作",
      key: "action",
      width: 220,
      render: (_, record) => (
        <Space>
          <Button type="link">
            <Link to={`/kb/${kbCode}/documents/${record.documentCode}`}>查看详情</Link>
          </Button>
          {record.status === "DISABLED" ? (
            <Button
              type="link"
              loading={togglingDocumentCode === record.documentCode}
              onClick={() => onEnable?.(record.documentCode)}
            >
              恢复文档
            </Button>
          ) : (
            <Popconfirm
              title="禁用文档"
              description="禁用后历史 chunk 和向量会保留，但不会参与检索和问答。"
              okText="确认禁用"
              cancelText="取消"
              onConfirm={() => onDisable?.(record.documentCode)}
            >
              <Button type="link" danger loading={togglingDocumentCode === record.documentCode}>
                禁用文档
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  return <Table rowKey="documentCode" columns={columns} dataSource={data} pagination={false} />;
}
