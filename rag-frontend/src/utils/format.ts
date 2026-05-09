import dayjs from "dayjs";

export function formatDateTime(value?: string) {
  if (!value) return "-";
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss");
}

export function formatFileSize(size?: number) {
  if (size === undefined) return "-";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function truncateText(text?: string, max = 120) {
  if (!text) return "-";
  if (text.length <= max) return text;
  return `${text.slice(0, max)}...`;
}
