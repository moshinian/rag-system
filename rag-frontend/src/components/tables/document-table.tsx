import { Button, Space, Table, Typography } from "antd";
import { Link } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import type { DocumentSummary } from "../../types/document";
import { formatDateTime, formatFileSize, truncateText } from "../../utils/format";
import { StatusBadge } from "../status/status-badge";

type DocumentTableProps = {
  kbCode: string;
  data: DocumentSummary[];
};

export function DocumentTable({ kbCode, data }: DocumentTableProps) {
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
      width: 120,
      render: (_, record) => (
        <Button type="link">
          <Link to={`/kb/${kbCode}/documents/${record.documentCode}`}>查看详情</Link>
        </Button>
      )
    }
  ];

  return <Table rowKey="documentCode" columns={columns} dataSource={data} pagination={false} />;
}
