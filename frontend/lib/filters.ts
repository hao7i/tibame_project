/**
 * 篩選條件 constants shared by the server-rendered results page and the client
 * facet rail.
 *
 * It lives here, not in FacetRail, because a "use client" module reaches a
 * Server Component as a client-reference proxy: importing this from there gave
 * an undefined max, which silently dropped the 價格上限 chip instead of failing.
 */
export const PRICE_CEILING = { min: 150, max: 1500, step: 10 };

/**
 * 呈現方式 — 列表 / 卡片 / 表格.
 *
 * The URL carries the ASCII value while the UI shows the 中文 label, so the
 * query string stays readable and a label can be reworded without breaking a
 * link someone already shared.
 */
export const VIEWS = [
  { value: "list", label: "列表" },
  { value: "cards", label: "卡片" },
  { value: "table", label: "表格" },
] as const;

export type ViewValue = (typeof VIEWS)[number]["value"];

export const DEFAULT_VIEW: ViewValue = "list";

/** A typo or an old link reads as 列表 rather than blanking the results. */
export function parseView(value?: string): ViewValue {
  return VIEWS.some((view) => view.value === value)
    ? (value as ViewValue)
    : DEFAULT_VIEW;
}
