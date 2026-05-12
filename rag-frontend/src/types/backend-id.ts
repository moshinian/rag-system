// 后端大量使用 Java Long / 雪花 ID。当前后端仍按 JSON number 返回，
// 前端不能假设这些值在 JS 里始终是安全整数，因此统一收口为 string | number。
export type BackendLongId = string | number;

export function toBackendIdParam(value: BackendLongId) {
  return String(value);
}
