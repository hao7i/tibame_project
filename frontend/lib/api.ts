/**
 * The one place the front end talks to the Spring backend.
 *
 * Every call runs on the Next.js server (Server Components), never in the
 * browser, so the backend origin stays private and CORS is not involved.
 * Next 16 does not cache fetch by default, which is what we want: the 6 小時
 * 取價 cache belongs to the backend, not to the page.
 */
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

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
  total: number;
  fetchedAt?: string;
  works: WorkSummary[];
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

/**
 * 搜尋 by 書名, 作者, 出版社 or ISBN.
 *
 * @param query  omit for 全部收錄書籍
 * @param format 載體 to narrow to; omit for 全部版本
 */
export function searchWorks(
  query?: string,
  format?: string,
): Promise<SearchResponse> {
  const params = new URLSearchParams();
  if (query) {
    params.set("q", query);
  }
  if (format) {
    params.set("format", format);
  }

  const queryString = params.toString();
  return getJson<SearchResponse>(
    queryString ? `/api/works?${queryString}` : "/api/works",
  );
}

export function listChannels(): Promise<Channel[]> {
  return getJson<Channel[]>("/api/channels");
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
