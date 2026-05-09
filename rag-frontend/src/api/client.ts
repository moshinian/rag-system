import type { ApiError, ApiResponse } from "../types/api";

type RequestOptions = {
  method?: "GET" | "POST";
  body?: BodyInit | null;
  headers?: Record<string, string>;
};

async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(url, {
    method: options.method ?? "GET",
    body: options.body,
    headers: options.headers
  });

  const payload = (await response.json()) as ApiResponse<T>;

  if (!response.ok || payload.code !== "SUCCESS") {
    const error = new Error(payload.message) as ApiError;
    error.code = payload.code;
    error.requestId = payload.requestId;
    throw error;
  }

  return payload.data;
}

export const apiClient = {
  get: <T>(url: string) => request<T>(url),
  postJson: <T>(url: string, body?: unknown) =>
    request<T>(url, {
      method: "POST",
      body: body === undefined ? null : JSON.stringify(body),
      headers: {
        "Content-Type": "application/json"
      }
    }),
  postForm: <T>(url: string, formData: FormData) =>
    request<T>(url, {
      method: "POST",
      body: formData
    })
};
