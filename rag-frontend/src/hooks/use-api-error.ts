import type { ApiError } from "../types/api";

export function getErrorMessage(error: unknown) {
  const apiError = error as ApiError | undefined;
  if (!apiError) return "未知错误";
  const suffix = apiError.requestId ? `（requestId: ${apiError.requestId}）` : "";
  return `${apiError.message}${suffix}`;
}
