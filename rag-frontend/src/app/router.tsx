import { Suspense, lazy } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";
import { Spin } from "antd";
import { AppShell } from "../components/app-shell/app-shell";

const DashboardPage = lazy(() =>
  import("../pages/dashboard").then((module) => ({ default: module.DashboardPage }))
);

const AgentPage = lazy(() =>
  import("../pages/agent").then((module) => ({ default: module.AgentPage }))
);

const DocumentDetailPage = lazy(() =>
  import("../pages/documents/detail").then((module) => ({ default: module.DocumentDetailPage }))
);

const DocumentsPage = lazy(() =>
  import("../pages/documents").then((module) => ({ default: module.DocumentsPage }))
);

const UploadPage = lazy(() =>
  import("../pages/documents/upload").then((module) => ({ default: module.UploadPage }))
);

const HealthPage = lazy(() =>
  import("../pages/health").then((module) => ({ default: module.HealthPage }))
);

const HistoryPage = lazy(() =>
  import("../pages/history").then((module) => ({ default: module.HistoryPage }))
);

const KnowledgeBasesPage = lazy(() =>
  import("../pages/knowledge-bases").then((module) => ({ default: module.KnowledgeBasesPage }))
);

const QaPage = lazy(() =>
  import("../pages/qa").then((module) => ({ default: module.QaPage }))
);

const RetrievalPage = lazy(() =>
  import("../pages/retrieval").then((module) => ({ default: module.RetrievalPage }))
);

/** 为懒加载页面包装统一加载态。 */
function withPageLoader(element: React.ReactNode) {
  return (
    <Suspense
      fallback={
        <div style={{ display: "flex", justifyContent: "center", padding: "96px 0" }}>
          <Spin size="large" />
        </div>
      }
    >
      {element}
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/knowledge-bases" replace /> },
      { path: "knowledge-bases", element: withPageLoader(<KnowledgeBasesPage />) },
      { path: "kb/:kbCode", element: withPageLoader(<DashboardPage />) },
      { path: "kb/:kbCode/agent", element: withPageLoader(<AgentPage />) },
      { path: "kb/:kbCode/upload", element: withPageLoader(<UploadPage />) },
      { path: "kb/:kbCode/documents", element: withPageLoader(<DocumentsPage />) },
      { path: "kb/:kbCode/documents/:documentCode", element: withPageLoader(<DocumentDetailPage />) },
      { path: "kb/:kbCode/retrieval", element: withPageLoader(<RetrievalPage />) },
      { path: "kb/:kbCode/qa", element: withPageLoader(<QaPage />) },
      { path: "kb/:kbCode/history", element: withPageLoader(<HistoryPage />) },
      { path: "health", element: withPageLoader(<HealthPage />) }
    ]
  }
]);
