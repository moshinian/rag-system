// 后端大量使用 Java Long / 雪花 ID。当前后端仍按 JSON number 返回，
// 前端不能假设这些值在 JS 里始终是安全整数，因此统一收口为 string | number。
export type BackendLongId = string | number;

/** 把后端 Long 风格 ID 转成可安全传输的路由参数。 */
export function toBackendIdParam(value: BackendLongId) {
  return String(value);
}
