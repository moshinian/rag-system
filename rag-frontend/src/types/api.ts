export type ApiResponse<T> = {
  code: string;
  message: string;
  data: T;
  requestId: string;
  timestamp: string;
};

export type PageResponse<T> = {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
};

export type ApiError = Error & {
  code?: string;
  requestId?: string;
};
