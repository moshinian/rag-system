import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppShell } from "../components/app-shell/app-shell";
import { DashboardPage } from "../pages/dashboard";
import { DocumentDetailPage } from "../pages/documents/detail";
import { DocumentsPage } from "../pages/documents";
import { UploadPage } from "../pages/documents/upload";
import { HealthPage } from "../pages/health";
import { HistoryPage } from "../pages/history";
import { KnowledgeBasesPage } from "../pages/knowledge-bases";
import { QaPage } from "../pages/qa";
import { RetrievalPage } from "../pages/retrieval";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/knowledge-bases" replace /> },
      { path: "knowledge-bases", element: <KnowledgeBasesPage /> },
      { path: "kb/:kbCode", element: <DashboardPage /> },
      { path: "kb/:kbCode/upload", element: <UploadPage /> },
      { path: "kb/:kbCode/documents", element: <DocumentsPage /> },
      { path: "kb/:kbCode/documents/:documentCode", element: <DocumentDetailPage /> },
      { path: "kb/:kbCode/retrieval", element: <RetrievalPage /> },
      { path: "kb/:kbCode/qa", element: <QaPage /> },
      { path: "kb/:kbCode/history", element: <HistoryPage /> },
      { path: "health", element: <HealthPage /> }
    ]
  }
]);
