import dayjs from "dayjs";
import type { RerankStatus, RetrievalMode } from "../types/qa";

/** 格式化日期时间。 */
export function formatDateTime(value?: string) {
  if (!value) return "-";
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss");
}

/** 格式化文件大小。 */
export function formatFileSize(size?: number) {
  if (size === undefined) return "-";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 截断过长文本。 */
export function truncateText(text?: string, max = 120) {
  if (!text) return "-";
  if (text.length <= max) return text;
  return `${text.slice(0, max)}...`;
}

/** 格式化检索模式展示文案。 */
export function formatRetrievalMode(mode?: RetrievalMode) {
  if (!mode) return "-";
  return mode === "HYBRID" ? "Hybrid" : "Dense";
}

/** 格式化融合策略展示文案。 */
export function formatFusionStrategy(strategy?: string) {
  if (!strategy) return "-";
  return strategy === "NONE" ? "无融合" : strategy;
}

/** 格式化重排序执行状态。 */
export function formatRerankStatus(status?: RerankStatus) {
  if (!status) return "-";
  const labels: Record<RerankStatus, string> = {
    DISABLED: "未启用",
    SKIPPED_EMPTY: "无候选",
    APPLIED: "已重排",
    DEGRADED: "已降级"
  };
  return labels[status];
}
