export const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

export const DEFAULT_PAGE = 1;

export const DEFAULT_PAGE_SIZE = 20;

const PAGE_SIZE_SET = new Set<number>(PAGE_SIZE_OPTIONS);

export type NormalizedPaginationParams = {
  page: number;
  pageSize: number;
  normalized: {
    page: string;
    pageSize: string;
  };
};

/** 解析正整数查询参数。 */
function parsePositiveInteger(value: string | null) {
  if (!value || !/^\d+$/.test(value)) {
    return undefined;
  }

  const parsed = Number(value);
  return parsed >= 1 ? parsed : undefined;
}

/** 归一化分页查询参数。 */
export function normalizePaginationParams(
  searchParams: URLSearchParams
): NormalizedPaginationParams {
  const page = parsePositiveInteger(searchParams.get("page")) ?? DEFAULT_PAGE;

  const candidatePageSize =
    parsePositiveInteger(searchParams.get("pageSize")) ?? DEFAULT_PAGE_SIZE;

  const pageSize = PAGE_SIZE_SET.has(candidatePageSize)
    ? candidatePageSize
    : DEFAULT_PAGE_SIZE;

  return {
    page,
    pageSize,
    normalized: {
      page: String(page),
      pageSize: String(pageSize)
    }
  };
}
