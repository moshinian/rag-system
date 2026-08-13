import { truncateText } from "../../utils/format";

type JsonBlockProps = {
  value: unknown;
  maxHeight?: number;
  maxRawLength?: number;
};

/** 统一展示格式化后的 JSON，解析失败时保留原始文本。 */
export function JsonBlock({ value, maxHeight = 260, maxRawLength = 4000 }: JsonBlockProps) {
  return (
    <pre
      style={{
        margin: 0,
        maxHeight,
        overflow: "auto",
        padding: 12,
        border: "1px solid #f0f0f0",
        borderRadius: 6,
        background: "#fafafa",
        color: "#1f1f1f",
        fontSize: 12,
        lineHeight: 1.6,
        whiteSpace: "pre"
      }}
    >
      {formatJson(value, maxRawLength)}
    </pre>
  );
}

function formatJson(value: unknown, maxRawLength: number) {
  const parsed = normalizeJsonValue(value, 0);
  if (typeof value === "string") {
    if (!value.trim()) {
      return "-";
    }
    if (parsed === value) {
      return truncateText(value, maxRawLength);
    }
  }
  try {
    const formatted = JSON.stringify(parsed, null, 2);
    return formatted ?? String(parsed);
  } catch {
    return truncateText(String(value), maxRawLength);
  }
}

function normalizeJsonValue(value: unknown, depth: number): unknown {
  if (depth > 8) {
    return value;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!looksLikeJsonContainer(trimmed)) {
      return value;
    }
    try {
      return normalizeJsonValue(JSON.parse(trimmed), depth + 1);
    } catch {
      return value;
    }
  }
  if (Array.isArray(value)) {
    return value.map((item) => normalizeJsonValue(item, depth + 1));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, item]) => [key, normalizeJsonValue(item, depth + 1)])
    );
  }
  return value;
}

function looksLikeJsonContainer(value: string) {
  return (value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"));
}
