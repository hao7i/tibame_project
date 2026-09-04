import type { Channel } from "@/lib/api";

/**
 * 進階搜尋 的條件與它的兩種寫法.
 *
 * The 查詢式預覽 the reader reads and the URL the 搜尋結果 page is fetched with are
 * built from this one module, on purpose: they are two renderings of the same
 * conditions, and if they were written separately the preview could promise a
 * search the results page never ran.
 *
 * The UI shows 中文 labels (書名, 電子書, 博客來) while the URL carries the codes
 * the API understands (title, EBOOK, BOOKS_TW) — the same split 載體 already
 * uses elsewhere.
 */

/** 搜尋欄位, in the order the design lists them. */
export const SEARCH_FIELDS = [
  { value: "title", label: "書名" },
  { value: "author", label: "作者" },
  { value: "publisher", label: "出版社" },
  { value: "translator", label: "譯者" },
  { value: "isbn", label: "ISBN" },
  { value: "series", label: "系列" },
] as const;

export const BOOLEAN_OPS = ["AND", "OR", "NOT"] as const;

export const FORMATS = [
  { value: "", label: "全部版本" },
  { value: "PAPER", label: "紙本書" },
  { value: "EBOOK", label: "電子書" },
] as const;

/** 「2024 或更早」 is written with a trailing dash, which the API reads as ≤. */
export const YEARS = [
  { value: "", label: "不限" },
  { value: "2026", label: "2026" },
  { value: "2025", label: "2025" },
  { value: "2024-", label: "2024 或更早" },
] as const;

export type AdvancedConditions = {
  field1: string;
  term1: string;
  op: (typeof BOOLEAN_OPS)[number];
  field2: string;
  term2: string;
  minPrice: string;
  maxPrice: string;
  year: string;
  channels: string[];
  format: string;
};

export const EMPTY_ADVANCED: AdvancedConditions = {
  field1: "title",
  term1: "",
  op: "AND",
  field2: "title",
  term2: "",
  minPrice: "",
  maxPrice: "",
  year: "",
  channels: [],
  format: "",
};

function labelOf(
  options: readonly { value: string; label: string }[],
  value: string,
): string {
  return options.find((option) => option.value === value)?.label ?? value;
}

/**
 * The 查詢式預覽 string, e.g.
 * `書名:"習慣" AND 出版社:"天下" AND price:[100 TO 400] AND shop:(博客來 OR Readmoo)`.
 *
 * A row with a blank 關鍵字 is not a condition, so it drops out and takes its
 * 布林 operator with it — which is exactly what the API does with it, and why
 * the preview can be trusted as a description of the search.
 */
export function previewQuery(
  conditions: AdvancedConditions,
  channels: Channel[],
): string {
  const rows = [
    { field: conditions.field1, term: conditions.term1.trim(), op: "" },
    { field: conditions.field2, term: conditions.term2.trim(), op: conditions.op },
  ].filter((row) => row.term !== "");

  const fielded = rows
    .map((row, index) => {
      const prefix = index === 0 ? "" : `${row.op} `;
      return `${prefix}${labelOf(SEARCH_FIELDS, row.field)}:"${row.term}"`;
    })
    .join(" ");

  let query = fielded || "*";

  if (conditions.minPrice || conditions.maxPrice) {
    query += ` AND price:[${conditions.minPrice || "*"} TO ${conditions.maxPrice || "*"}]`;
  }
  if (conditions.year) {
    query += ` AND year:"${labelOf(YEARS, conditions.year)}"`;
  }
  if (conditions.channels.length > 0) {
    const names = conditions.channels.map(
      (code) => channels.find((channel) => channel.code === code)?.name ?? code,
    );
    query += ` AND shop:(${names.join(" OR ")})`;
  }
  if (conditions.format) {
    query += ` AND format:"${labelOf(FORMATS, conditions.format)}"`;
  }

  return query;
}

/** The same conditions as the 搜尋結果 URL the 執行搜尋 button navigates to. */
export function advancedToParams(
  conditions: AdvancedConditions,
  channels: Channel[],
): URLSearchParams {
  const params = new URLSearchParams();

  const term1 = conditions.term1.trim();
  const term2 = conditions.term2.trim();

  if (term1) {
    params.set("field1", conditions.field1);
    params.set("term1", term1);
  }
  if (term2) {
    params.set("field2", conditions.field2);
    params.set("term2", term2);
  }
  // 布林 only travels when there are two conditions for it to join.
  if (term1 && term2) {
    params.set("op", conditions.op);
  }

  if (conditions.minPrice.trim()) {
    params.set("minPrice", conditions.minPrice.trim());
  }
  if (conditions.maxPrice.trim()) {
    params.set("maxPrice", conditions.maxPrice.trim());
  }
  if (conditions.year) {
    params.set("year", conditions.year);
  }
  if (conditions.format) {
    params.set("format", conditions.format);
  }

  // Kept in the 通路 order the rest of the site presents, not in click order,
  // so the same selection always produces the same URL.
  for (const channel of channels) {
    if (conditions.channels.includes(channel.code)) {
      params.append("channel", channel.code);
    }
  }

  return params;
}
