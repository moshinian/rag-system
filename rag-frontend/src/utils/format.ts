import dayjs from "dayjs";
import type { RetrievalMode } from "../types/qa";

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
