import {
  ArrowLeftOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  HeartOutlined,
  MessageOutlined,
  RadarChartOutlined,
  UploadOutlined
} from "@ant-design/icons";
import { Button, Layout, Menu, Select, Space, Typography } from "antd";
import { useMemo } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listKnowledgeBases } from "../../api/knowledge-base";
import { useAppStore } from "../../app/store";
import { useCurrentKb } from "../../hooks/use-current-kb";

const { Header, Sider, Content } = Layout;

/** 渲染复用组件。 */
export function AppShell() {
  const location = useLocation();

  const navigate = useNavigate();

  const kbCode = useCurrentKb();

  const setCurrentKbCode = useAppStore((state) => state.setCurrentKbCode);

  const { data } = useQuery({
    queryKey: ["knowledgeBases", "shell"],
    queryFn: () => listKnowledgeBases({ pageNo: 1, pageSize: 100 })
  });

  const menuItems = useMemo(() => {
    if (!kbCode) {
      return [
        { key: "/knowledge-bases", icon: <DatabaseOutlined />, label: <Link to="/knowledge-bases">知识库</Link> },
        { key: "/health", icon: <HeartOutlined />, label: <Link to="/health">系统健康</Link> }
      ];
    }

    return [
      { key: `/kb/${kbCode}`, icon: <DatabaseOutlined />, label: <Link to={`/kb/${kbCode}`}>概览</Link> },
      { key: `/kb/${kbCode}/upload`, icon: <UploadOutlined />, label: <Link to={`/kb/${kbCode}/upload`}>文档接入</Link> },
      { key: `/kb/${kbCode}/documents`, icon: <FileTextOutlined />, label: <Link to={`/kb/${kbCode}/documents`}>文档管理</Link> },
      { key: `/kb/${kbCode}/retrieval`, icon: <FileSearchOutlined />, label: <Link to={`/kb/${kbCode}/retrieval`}>检索调试</Link> },
      { key: `/kb/${kbCode}/qa`, icon: <MessageOutlined />, label: <Link to={`/kb/${kbCode}/qa`}>问答台</Link> },
      { key: `/kb/${kbCode}/history`, icon: <RadarChartOutlined />, label: <Link to={`/kb/${kbCode}/history`}>问答记录</Link> },
      { key: "/health", icon: <HeartOutlined />, label: <Link to="/health">系统健康</Link> }
    ];
  }, [kbCode]);

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider width={250} theme="light" className="shell-sider">
        <div className="brand-block">
          <Typography.Title level={4} style={{ margin: 0 }}>
            RAG Console
          </Typography.Title>
          <Typography.Text type="secondary">
            企业知识库问答工作台
          </Typography.Text>
        </div>
        <Menu mode="inline" selectedKeys={[location.pathname]} items={menuItems} />
      </Sider>
      <Layout>
        <Header className="shell-header">
          <Space size="large" style={{ width: "100%", justifyContent: "space-between" }}>
            <Space size="middle">
              {kbCode ? (
                <Button
                  icon={<ArrowLeftOutlined />}
                  onClick={() => {
                    setCurrentKbCode(undefined);
                    navigate("/knowledge-bases");
                  }}
                >
                  返回知识库列表
                </Button>
              ) : null}
              <Typography.Text strong>
                {kbCode ? `当前知识库: ${kbCode}` : "请选择或创建知识库"}
              </Typography.Text>
            </Space>
            <Select
              allowClear
              style={{ width: 280 }}
              placeholder="快速切换知识库"
              value={kbCode}
              options={data?.records.map((item) => ({
                label: `${item.name} (${item.kbCode})`,
                value: item.kbCode
              }))}
              onChange={(value) => {
                if (!value) {
                  setCurrentKbCode(undefined);
                  navigate("/knowledge-bases");
                  return;
                }
                navigate(`/kb/${value}`);
              }}
            />
          </Space>
        </Header>
        <Content className="shell-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
