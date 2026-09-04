/**
 * The one place the front end talks to the Spring backend.
 *
 * Every call runs on the Next.js server (Server Components), never in the
 * browser, so the backend origin stays private and CORS is not involved.
 * Next 16 does not cache fetch by default, which is what we want: the 6 小時
 * 取價 cache belongs to the backend, not to the page.
 */
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/** A 前台會員, as the front end is allowed to see one. Never any password. */
export type Member = {
  id: number;
  email: string;
};

/** 載體 — the two publication media the site covers. */
export type Format = "PAPER" | "EBOOK";

export type ChannelPrice = {
  channel: string;
  channelCode: string;
  price: number;
  format: Format;
};

/** 最低價 under whatever filters are active, always computed by the backend. */
export type BestPrice = {
  channel: string;
  channelCode: string;
  price: number;
  discountPercent: number;
  /** Absent when 售價 is at or above 定價, so there is no 折扣 to state. */
  discountLabel?: string;
  /** 較定價省 — how much less than the 作品 定價 this 報價 asks. */
  savingVsListPrice: number;
  /** Absent when the 通路 has no usable link. */
  purchaseUrl?: string;
};

/** One row of the 各通路報價 table on 單書比價. */
export type OfferView = {
  channel: string;
  channelCode: string;
  format: Format;
  formatLabel: string;
  stockStatus: string;
  price: number;
  discountLabel?: string;
  purchaseUrl?: string;
  /** The 最低價 row, which the design tints and tags. */
  best: boolean;
};

export type WorkDetail = {
  isbn: string;
  title: string;
  author: string;
  publisher: string;
  publicationYear: number;
  category: string;
  blurb?: string;
  listPrice: number;
  channelCount: number;
  fetchedAt?: string;
  /** Absent when no 報價 survives the 載體 filter. */
  bestPrice?: BestPrice;
  offers: OfferView[];
};

export type WorkSummary = {
  /** 紙本 ISBN — how a 作品 is addressed in URLs. */
  isbn: string;
  title: string;
  author: string;
  publisher: string;
  publicationYear: number;
  category: string;
  listPrice: number;
  channelCount: number;
  bestPrice?: BestPrice;
  channelPrices: ChannelPrice[];
};

export type SearchResponse = {
  /** Absent when the caller asked for 全部收錄書籍. */
  query?: string;
  /** Every match after 篩選, before 分頁. */
  total: number;
  page: number;
  pageSize: number;
  /** 0 when nothing matched. */
  totalPages: number;
  fetchedAt?: string;
  works: WorkSummary[];
};

/**
 * 篩選條件 options with counts that ignore the 搜尋 term and the other facets,
 * but do respect the active 載體 — hence their own endpoint.
 */
export type Facets = {
  channels: { code: string; name: string; count: number }[];
  categories: { name: string; count: number }[];
};

export type Channel = {
  code: string;
  name: string;
  kind: string;
};

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    throw new Error(`後端 ${path} 回應 ${response.status}`);
  }

  return (await response.json()) as T;
}

/** Everything the 搜尋結果 screen can ask for, as it arrives from the URL. */
export type WorkSearchParams = {
  /** omit for 全部收錄書籍 */
  q?: string;
  /** 載體; omit for 全部版本 */
  format?: string;
  /** 通路 codes; OR within the group */
  channel?: string[];
  /** 分類 names; OR within the group */
  category?: string[];
  /** 價格下限, applied to the computed 最低價 */
  minPrice?: string;
  /** 價格上限, applied to the computed 最低價 */
  maxPrice?: string;
  /** 出版年: "2025" for that year, "2024-" for 2024 或更早 */
  year?: string;
  /** 進階搜尋 第一列 搜尋欄位 and 關鍵字 */
  field1?: string;
  term1?: string;
  /** How 第二列 joins 第一列: AND / OR / NOT */
  op?: string;
  field2?: string;
  term2?: string;
  /** 1-based; the backend clamps out-of-range values */
  page?: string;
};

/** 搜尋 by 書名, 作者, 出版社 or ISBN, narrowed by 載體, 通路, 分類 and 價格上限. */
export function searchWorks(
  search: WorkSearchParams = {},
): Promise<SearchResponse> {
  const params = new URLSearchParams();

  if (search.q) {
    params.set("q", search.q);
  }
  if (search.format) {
    params.set("format", search.format);
  }
  for (const key of
    ["minPrice", "maxPrice", "year", "field1", "term1", "op", "field2", "term2"] as const) {
    const value = search[key];
    if (value) {
      params.set(key, value);
    }
  }
  if (search.page) {
    params.set("page", search.page);
  }
  for (const code of search.channel ?? []) {
    params.append("channel", code);
  }
  for (const name of search.category ?? []) {
    params.append("category", name);
  }

  const queryString = params.toString();
  return getJson<SearchResponse>(
    queryString ? `/api/works?${queryString}` : "/api/works",
  );
}

export function listChannels(): Promise<Channel[]> {
  return getJson<Channel[]>("/api/channels");
}

export function listFacets(format?: string): Promise<Facets> {
  return getJson<Facets>(
    format ? `/api/facets?format=${encodeURIComponent(format)}` : "/api/facets",
  );
}

/**
 * One 作品 with every 通路 報價, for 單書比價.
 *
 * Returns null when no 作品 carries that ISBN, so the page can render the 404
 * rather than treat a mistyped URL as a server failure.
 *
 * @param isbn   either 版本 ISBN; both address the same 作品
 * @param format 載體 to narrow to; omit for 全部版本
 * @param sort   PRICE (價格低→高) or CHANNEL (依通路)
 */
export async function fetchWorkDetail(
  isbn: string,
  format?: string,
  sort?: string,
): Promise<WorkDetail | null> {
  const params = new URLSearchParams();
  if (format) {
    params.set("format", format);
  }
  if (sort) {
    params.set("sort", sort);
  }

  const queryString = params.toString();
  const path = `/api/works/${encodeURIComponent(isbn)}${
    queryString ? `?${queryString}` : ""
  }`;

  const response = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Accept: "application/json" },
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`後端 ${path} 回應 ${response.status}`);
  }

  return (await response.json()) as WorkDetail;
}
