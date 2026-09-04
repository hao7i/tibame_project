import type { Channel } from "@/lib/api";

/**
 * 進階搜尋 的條件, and the 搜尋結果 URL they turn into.
 *
 * The UI shows 中文 labels (書名, 金石堂) while the URL carries the codes the API
 * understands (title, KINGSTONE), so a label can be reworded without breaking a
 * link someone already shared.
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
};

/* previewQuery and its labelOf helper lived here until the 查詢式預覽 卡 was
   removed from 進階搜尋; nothing renders 中文 labels from this module any more. */

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

  // Kept in the 通路 order the rest of the site presents, not in click order,
  // so the same selection always produces the same URL.
  for (const channel of channels) {
    if (conditions.channels.includes(channel.code)) {
      params.append("channel", channel.code);
    }
  }

  return params;
}
