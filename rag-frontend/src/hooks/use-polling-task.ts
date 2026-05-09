import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { getDocument, listDocumentChunks, listIndexingTasks } from "../api/document";

export function useDocumentMonitor(kbCode: string, documentCode: string, enabled = true) {
  const tasksQuery = useQuery({
    queryKey: ["indexingTasks", kbCode, documentCode],
    queryFn: () => listIndexingTasks(kbCode, documentCode),
    enabled,
    refetchInterval: (query) => {
      const tasks = query.state.data ?? [];
      const current = tasks[0];
      if (!current) return false;
      return current.status === "QUEUED" || current.status === "RUNNING" ? 3000 : false;
    }
  });

  const detailQuery = useQuery({
    queryKey: ["documentDetail", kbCode, documentCode],
    queryFn: () => getDocument(kbCode, documentCode),
    enabled,
    refetchInterval: () => {
      const current = tasksQuery.data?.[0];
      return current && (current.status === "QUEUED" || current.status === "RUNNING")
        ? 3000
        : false;
    }
  });

  const chunksQuery = useQuery({
    queryKey: ["documentChunks", kbCode, documentCode],
    queryFn: () => listDocumentChunks(kbCode, documentCode),
    enabled,
    refetchInterval: () => {
      const current = tasksQuery.data?.[0];
      return current && (current.status === "QUEUED" || current.status === "RUNNING")
        ? 5000
        : false;
    }
  });

  const progress = useMemo(() => {
    const task = tasksQuery.data?.[0];
    if (!task || !task.chunkCount || task.embeddedChunkCount === undefined) return undefined;
    return Math.round((task.embeddedChunkCount / task.chunkCount) * 100);
  }, [tasksQuery.data]);

  return {
    tasksQuery,
    detailQuery,
    chunksQuery,
    progress
  };
}
