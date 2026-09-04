# Handoff: 書籍比價入口網站（Book Price Portal）

## Overview
A responsive Traditional-Chinese web application that aggregates the selling price of a single book
across six Taiwanese online bookstores (博客來 / 誠品線上 / 金石堂 / 讀冊生活 / 樂天Kobo / Readmoo),
similar to a price-comparison site. Users search by title / author / publisher / ISBN, compare offers
per channel, and — once logged in — track books with a target price and receive drop notifications.

Desktop-first (1180px design width) with a 390px mobile rendition of every screen.

## About the Design Files
The files in this bundle are **design references authored in HTML** — a prototype showing the intended
look, copy and behaviour. They are **not production code to copy**. The task is to **recreate these
designs in the target codebase's existing environment** (React, Vue, Next.js, native, etc.) using its
established patterns, routing and component library. If no environment exists yet, pick the most
appropriate framework for the project and implement the designs there.

`Book Price Portal.dc.html` is a single-file prototype: markup in one template, state/handlers in a
`class Component` block at the bottom (`state`, `renderVals()`), plus `support.js` (the prototype
runtime — **do not port it**, it only makes the reference file open in a browser).

## Fidelity
**High fidelity (hifi).** Final colors, type, spacing, copy and interactions. Recreate the UI closely,
but express it with the target codebase's own component primitives. All values come from the
**Industry** design system (`_ds/industry-…/styles.css`, guide in `_ds/industry-…/readme.md`) —
port those CSS variables as the app's tokens rather than hard-coding hexes.

Data in the prototype (books, prices, price history) is **mock/示意資料**; real data comes from the
channel scrapers/APIs. Every price surface carries the disclaimer copy noted below.

## Screens / Views

Both device widths render the same five routes. The prototype's top page-tabs strip (首頁 / 搜尋結果 /
單書比價 / 追蹤清單 / 進階搜尋 / 登入) is a **prototype navigation aid only — do not build it**;
in the real app these are routes.

### 0. Global chrome
- **Desktop header** (`.nav`, padding 14px 28px): full-bleed field `--color-accent-900` with
  `--color-bg` text. Brand 書價 (Barlow Condensed 600, 18px) + `BOOKPRICE.TW` (Barlow 400, 12px,
  `--color-accent-300`, 9px left margin). Links (14px): 首頁（active, `--color-accent-300`）、進階搜尋、
  追蹤清單（shows ` (n)` when items are tracked）、通路一覽. Right side: 登出 (ghost,
  `--color-accent-300`) when logged in, otherwise 登入 (secondary button, light text, border
  `--color-accent-600`). Link hover on the dark bar: `--color-accent-300`.
- **Mobile header** (390px): same dark field, 13px 16px padding — hamburger icon button (36×36),
  brand 書價 (16px), then 追蹤 n / 登入 button on the right.
- **Footer** (desktop only): 1px top divider, padding 18px 28px — "BOOKPRICE.TW · 價格每 6 小時更新一次"
  (12px muted) + links 收錄通路 / 意見回饋 / 資料來源說明.

### 1. 首頁 (Home)
Purpose: enter a search.
- **Hero band**: full-width `--color-accent-100` field, padding 52px 28px 44px, 1px bottom divider.
  Content column max-width 860px.
  - Kicker h6 "六大通路一次比" (`--color-accent-700`, uppercase-tracked).
  - h2 "輸入書名，看它在台灣各網路書店賣多少" 36px.
  - Format segmented control `.seg`: 全部版本 / 紙本書 / 電子書 (selected = `--color-accent` fill,
    `--color-bg` label).
  - Search row (flex, gap 10px): `.input` (flex:1, min-width:0, min-height 48px, 16px,
    placeholder 「書名、作者、出版社或 ISBN」) + primary 比價 button (blueprint frame, min-height 48px,
    padding-inline 34px) + secondary 進階搜尋 (flex:none, nowrap on both buttons).
  - Hot searches: label 熱門搜尋 (12px muted) + `.tag.tag-outline` buttons on `--color-bg`
    (原子習慣 / 人類大歷史 / 被討厭的勇氣 / 設計的設計 / 如何閱讀一本書) — clicking runs that search.
- **收錄通路 strip**: 6 equal cells, 1px gaps over `--color-divider`, each cell padding 16px 14px,
  tinted per channel (see Design Tokens → channel tints); shop name 18px Barlow Condensed 600,
  sub-label (紙本 / 電子書 etc.) 11px in the paired sub-color.
- **運作方式 card** (max-width 640px, `.card.blueprint`): kicker 運作方式 + three numbered rows
  (01 輸入書名或 ISBN / 02 比較售價與版本 / 03 設定目標價追蹤), each separated by a 1px top divider.
- Mobile: h2 26px, stacked full-width input + 比價 + 進階搜尋 buttons (min-height 46/44px),
  wrapped hot-search tags. The 收錄通路 strip and 運作方式 card are desktop-only.

### 2. 搜尋結果 (Results)
Purpose: compare books matching the query; three presentation modes.
- **Search bar row**: 1px bottom divider, padding 16px 28px — input (flex 0 1 520px) + 比價 primary +
  進階搜尋 ghost.
- **Layout**: CSS grid `236px 1fr`; left rail has a 1px right divider, padding 22px 22px 40px.
- **Facet rail**: h6 篩選條件, then groups 通路 (6 shops) and 分類 (心理勵志 / 人文史地 / 藝術設計).
  Each option is a row: 13px label + square 13×13 checkbox (1px divider border, fill `--color-accent`
  when on, label color `--color-accent-700` when on) and a right-aligned count (11px muted).
  Then a range slider 價格上限 NT$ n (min 150, max 700, step 10, `accent-color: var(--color-accent)`)
  and a full-width 清除篩選 secondary button.
- **Result header**: h3 「<query>」比價結果 (or 全部收錄書籍) + sub-line
  "共 n 筆 · 六家通路 · 取價時間 YYYY/MM/DD hh:mm"; on the right a 呈現方式 label + `.seg`
  列表 / 卡片 / 表格.
- **Active-filter chips**: `.tag.tag-accent` with a ✕, click removes that facet.
- **列表 (list) view** — one row per book, 1px top divider, padding 20px 0, grid `64px 1fr 300px` gap 20px:
  1. Cover placeholder: `.blueprint.duotone`, height 92px, `--color-surface`, centered 10px label 封面.
  2. Title 21px Barlow Condensed 600 (click → detail), byline 13px muted, tag row
     (分類 `.tag-neutral`, 定價 n `.tag-outline`, n 個通路有貨 `.tag-accent`), then up to four
     shop/price mini pairs (shop 11px muted + price 15px condensed; cheapest in `--color-accent-700`).
  3. Right column, right-aligned: 「最低價 · <shop>」 11px uppercase muted, price 32px Barlow
     Condensed 600 `--color-accent-700`, 「約 n 折｜共 n 個通路」 12px muted, then
     ＋追蹤價格 / 追蹤中 secondary toggle (filled `--color-accent` + `--color-bg` when tracked) and a
     看全部報價 primary button.
- **卡片 (cards) view**: 3-column grid, gap 24px, `.card.blueprint` — 120px duotone cover placeholder,
  kicker 分類, 17px title (click → detail), byline, then 最低 · <shop> + 26px price and a
  `.tag-accent` 折扣, `.card-meta` 定價 n · n 個通路, and a ☆/★ toggle + 比價 primary button.
- **表格 (table) view**: `.table` with a `--color-accent-900` header row, labels in
  `--color-accent-200`. Columns: 書名 (16px condensed, clickable) / 作者／出版 / one right-aligned
  column per shop (NT$n, cheapest bold `--color-accent-700`, missing offer "—" in
  `--color-neutral-500`) / 最低價 (17px condensed `--color-accent-700`) / 追蹤 (☆/★ ghost button).
- **Pagination**: 上一頁 / 1-4 / 下一頁 secondary buttons (current filled `--color-accent`) + 
  「第 n 頁 / 共 4 頁」 12px muted.
- Mobile: search row + horizontally scrollable facet chips row (1px dividers above/below), a compact
  result count + `.seg` switcher, then rows of `56px 1fr` — 76px duotone cover, 18px title, 12px byline,
  22px `--color-accent-700` price + shop + 折扣 tag, then ☆ toggle + 「看 n 個報價」 primary
  (min-height 38px).

### 3. 單書比價 (Book detail) — the core screen
Purpose: see every channel's offer for one book and start tracking it.
- 「‹ 回搜尋結果」 ghost button, then grid `220px 1fr 300px`, gap 32px, align-items start.
- **Left**: 300px-tall duotone cover placeholder (blueprint frame).
- **Middle**: kicker = 分類; h2 title 32px; byline 14px muted; tag row
  (ISBN <isbn> outline / 定價 NT$ n neutral / n 個通路有貨 accent); 14px blurb (max-width 520px).
  Then 各通路報價 h4 with the 版本 `.seg` (全部版本/紙本書/電子書) and a sort `.seg`
  (價格低→高 / 依通路) pushed right.
  **Offer table** (`.table`, dark `--color-accent-900` header, labels `--color-accent-200`):
  通路 (9×9 channel swatch + name 16px condensed + 最低價 accent tag on the cheapest row) /
  版本 / 庫存／到貨 (muted) / 折扣 (right) / 售價 (right, 19px condensed; cheapest
  `--color-accent-700`) / 前往購買 secondary button. Cheapest row background `--color-accent-100`.
  Below: 11.5px muted disclaimer 「價格為示意資料，實際售價以各通路網站為準。最後更新 …」.
- **Right column** (three `.card.blueprint`, gap 20px):
  1. 目前最低 — 40px `--color-accent-700` price, 「<shop>｜約 n 折｜較定價省 NT$ n」,
     full-width primary 前往 <shop>, then ＋追蹤價格 / 追蹤中 toggle.
  2. 近 90 天價格走勢 — inline SVG polyline sparkline (viewBox 240×64, stroke
     `var(--color-accent)` 1.5px, 1.5px dots, last point r=3 filled accent) + `.card-meta`
     「區間 NT$ low – high」.
  3. 降價通知 — body copy switches on auth: logged in 「設定目標價，低於此價時以 email 通知。」,
     logged out 「降價通知為會員功能，登入後可設定目標價。」; 目標價 NT$ field; button label
     設定通知 / 登入以設定通知.
- Mobile: `88px 1fr` header block (120px cover, 21px title, 26px price, shop｜折扣), full-width
  前往 <shop> + ☆ toggle (min-height 44px), full-width 版本 `.seg`, then one offer row per channel
  (swatch + shop + 版本｜庫存, right-aligned price + 折扣, 前往 button), plus the disclaimer.

### 4. 追蹤清單 (Watch list) — members only
Purpose: manage tracked books and target prices.
- **Logged out** (`watchLocked`): a single `.card.blueprint` (max-width 480px, padding `--space-8`)
  — kicker 會員專屬功能, title 追蹤清單需要登入, body 「登入後可追蹤書籍、設定目標價，並在低於目標價時收到 email 通知。」,
  primary 登入 / 註冊. Same card, full-width button, in the mobile frame.
- **Logged in**: header — kicker 會員專屬, h2 追蹤清單, sub-line
  「n 本書 · 價格每 6 小時更新 · 達目標價時 email 通知」; right side 繼續搜尋書籍 / 清空清單 secondary buttons.
- **Empty state**: `.card.blueprint` max-width 520px — 清單是空的 / 還沒有追蹤任何書籍 /
  「在搜尋結果或單書比價頁按「＋ 追蹤價格」，就會出現在這裡，並可設定目標價。」 + 前往搜尋 primary.
- **Table** (dark accent-900 header): 書名 (17px condensed, click → detail) / 作者／出版 /
  目前最低價 (right, 19px `--color-accent-700`) / 通路 (swatch + name) / 目標價 (inline `.input`,
  placeholder NT$) / 通知狀態 tag / 比價 + 移除 ghost buttons.
  Status logic: no target → 「未設目標價」 `.tag-neutral`; best ≤ target → 「已達目標價」
  `.tag-accent` and row background `--color-accent-100`; otherwise 「尚差 NT$ n」 `.tag-neutral`.
  Footnote 11.5px muted: 「目標價達成時會寄送 email 通知。價格為示意資料，每 6 小時更新一次。」
- Mobile: stacked cards per row — title, byline, 22px price + shop + status tag, 目標價 field + 移除.

### 5. 進階搜尋 (Advanced search)
Purpose: build a fielded query with price limits.
- Grid `1fr 320px`, gap 44px, padding 40px 28px 48px.
- Kicker 進階搜尋, h2 組合欄位與價格條件.
- **Two fixed condition rows** (no add-row control): grid `88px 150px 1fr`, gap 12px —
  operator select (row 1 label 條件 with the single option 「—」; row 2 label 布林 with AND / OR / NOT),
  搜尋欄位 select (書名 / 作者 / 出版社 / 譯者 / ISBN / 系列), 關鍵字 input.
- 重設 ghost button; `.hr` divider (24px+ margins).
- Three fields: 價格下限 NT$ / 價格上限 NT$ / 出版年 (不限 / 2026 / 2025 / 2024 或更早).
- 限定通路: 3-column grid of 6 square checkboxes (14×14, fill `--color-accent` when on).
- 版本: three radios (15px circle, `--color-accent` fill + `inset 0 0 0 3.5px var(--color-bg)` ring
  when selected) — 全部版本 / 紙本書 / 電子書.
- Actions: primary 執行搜尋 (blueprint, min-height 42px) + secondary 回簡易搜尋.
- **Right rail**: 查詢式預覽 card — monospace 12.5px live query string, e.g.
  `書名:"習慣" AND 出版社:"天下" AND price:[100 TO 400] AND shop:(博客來 OR Readmoo) AND format:"電子書"`
  (`*` when empty) + `.card-meta` 「系統會依此條件向各通路取價」; plus a 說明 card.
- Mobile: each condition row becomes a bordered block (operator 92px + 欄位 flexed, then 關鍵字),
  two price fields side by side, the monospace preview in `--color-accent-700`, full-width 執行搜尋.

### 6. 登入 (Login)
- Desktop: two equal columns, min-height 520px. Left is a `--color-accent-900` field, padding 48px 40px,
  space-between: kicker 會員功能 (`--color-accent-300`) + h2 「登入後可追蹤書價並收到降價通知」 34px
  `--color-bg` (max-width 320px); bottom list (13px, line-height 1.9, `--color-accent-200`):
  追蹤清單同步各裝置 / 目標價達成 email 通知 / 保留搜尋條件與比價紀錄.
  Right column padding 48px 44px, max-width 420px, centered: h3 會員登入, a 13px muted hint line
  (「追蹤清單為會員功能，登入後才能使用。」 or, when the user clicked 追蹤 first,
  「登入後即可追蹤《書名》的價格並收到降價通知。」), 電子郵件 + 密碼 fields (min-height 42px),
  記住此裝置 radio, full-width primary 登入 (min-height 44px), then 忘記密碼 / 註冊新帳號 links.
- Mobile: dark block (padding 30px 20px) with kicker + h3, then the form (fields min-height 44px,
  button 46px).

## Interactions & Behavior
- **Search**: typing updates the query; Enter or 比價 navigates to results (page reset to 1).
  Hot-search tags and 熱門 chips set the query and navigate. The prototype filters mock data by
  substring over title+byline+category+ISBN and keeps the unfiltered set when nothing matches.
- **Facets**: multi-select per group, OR within a group and AND across groups; 通路 facets also
  restrict which offers count toward 最低價; chips mirror the active set and remove on click;
  清除篩選 resets facets and the price ceiling.
- **Format filter** (全部版本 / 紙本書 / 電子書) applies to both the list's best-price computation and
  the detail offer table. 電子書 offers are those whose 版本 starts with 「電子書」.
- **Sort** on detail: 價格低→高 (default) or 依通路 (fixed channel order).
- **View switch** 列表 / 卡片 / 表格 is persistent state, shared by desktop and mobile.
- **Tracking (auth-gated)**: pressing ＋追蹤價格 / ☆ while logged out routes to 登入 and remembers the
  pending title; after login that book is added and the user lands on the book's detail. Logged in, the
  toggle adds/removes immediately; the nav count updates. 登出 clears the session and the list.
- **Target price**: editing the 目標價 field recomputes the notification status live
  (未設目標價 / 尚差 NT$ n / 已達目標價 + tinted row).
- **Navigation**: 追蹤清單 nav link always routes to the watch list, which renders either the locked
  card or the list. 移除 deletes one row, 清空清單 empties the list.
- **States to build that the prototype does not cover**: loading/skeleton while channels are being
  polled, a per-channel fetch-failure row, empty search results, and price-refresh timestamps.
- **Responsive**: 1180px desktop and 390px mobile are specified. Between them, collapse the results
  grid (rail becomes a filter sheet), let the 收錄通路 strip wrap to 3×2, stack the detail's three
  columns, and switch the offer/watch tables to stacked rows. A 768px tablet breakpoint is not designed
  yet.
- Interaction states are the design system's: `:hover` tint, pressed = one accent step deeper,
  `:focus-visible` = 2px `--color-accent` outline with 2px offset. Never leave browser defaults.

## State Management
Prototype state (rename freely, but the shape maps 1:1 to real needs):
- `q` — search query. `format` — 全部版本 | 紙本書 | 電子書. `view` — 列表 | 卡片 | 表格.
- `sort` — 價格低→高 | 依通路. `page` — pagination index.
- `on` — facet selection keyed `"<group>|<label>"` (groups: `shop`, `category`).
- `maxPrice` — price-ceiling slider value.
- `loggedIn` — session flag. `pending` — title awaiting tracking through the login detour.
- `watch` — `{ [title]: { target: string } }`.
- `current` — index of the book shown on the detail screen (a real app uses a route param / ISBN).
- `rows` — advanced-search conditions `[{op, field, term}]` (fixed length 2), plus `priceFrom`,
  `priceTo`, `advShops`.
- Derived: filtered book list, per-book best offer (after format/facet filtering), discount %,
  savings vs list price, 90-day trend min/max, notification status.

Data fetching in the real app: one endpoint per search that fans out to the six channels (cache ~6h,
per-channel failure tolerated), one endpoint for a book's offers + price history, and authenticated
CRUD for the watch list + target-price notification job.

## Design Tokens
All from `_ds/industry-…/styles.css` (`:root`). Use the variables, not the literals.

- **Core**: bg `#f2f2f3`, surface `#e9e9ea`, text `#1d1f20`, accent `#5980a6`,
  divider `color-mix(in srgb, #1d1f20 16%, transparent)`.
- **Accent ramp**: 100 `#eef6ff`, 200 `#d6ebff`, 300 `#b5d9fd`, 400 `#94bce3`, 500 `#749dc4`,
  600 `#597ea3`, 700 `#416180`, 800 `#2c455d`, 900 `#1d2d3d`.
- **Accent-2 ramp** (same hue family, used only for channel tints): 100 `#eef6ff`, 200 `#d6ebff`,
  300 `#bdd8f2`, 400 `#9ebbd8`, 500 `#7e9cb8`, 600 `#627d98`, 700 `#486077`, 800 `#314457`,
  900 `#1f2d3a`.
- **Neutral ramp**: 100 `#f5f5f8` → 900 `#2b2b2d`.
- **Channel tints** (`background`, `text`, `sub-text`):
  博客來 accent-200 / accent-900 / accent-800 · 誠品線上 accent-300 / accent-900 / accent-800 ·
  金石堂 accent-2-200 / accent-2-900 / accent-2-800 · 讀冊生活 accent-2-400 / accent-2-900 /
  accent-2-900 · 樂天Kobo accent-700 / bg / accent-200 · Readmoo accent-800 / bg / accent-200.
  The same background value is the 9×9 swatch color in offer and watch tables.
- **Type**: headings Barlow Condensed 600, body Barlow 400 (Google Fonts, imported by styles.css).
  Scale h1 42 / h2 32 / h3 25 / h4 20 / h5 16 / h6 13px uppercase tracked 0.08em; body 15px/1.55;
  headings line-height 1.12, letter-spacing -0.015em. Prices use Barlow Condensed 600 at 19–40px.
  Query previews use `ui-monospace, Menlo, monospace` 11.5–12.5px.
- **Spacing** (0.85× scale): `--space-1` 3.4 / 2 6.8 / 3 10.2 / 4 13.6 / 6 20.4 / 8 27.2px.
- **Radius**: sm 2 / md 4 / lg 7px — but cards, buttons, inputs, tags, segmented controls and dialogs
  are overridden to **0** (square corners).
- **Shadow**: sm `0 1px 2px rgba(43,43,45,.14)`, md `0 3px 10px rgba(43,43,45,.16)`,
  lg `0 12px 32px rgba(43,43,45,.22)`.
- **Blueprint frame**: 1px divider border + four 11×11 `+` registration marks offset -6px at the
  corners (`.blueprint` + `<i class="corner tl|tr|bl|br">`). Every card, framed figure and primary
  button wears it; do not drop the marks.
- **Contrast rules learned in review**: accent text on the light ground must use accent-700+;
  light text on a tint needs accent-700 or deeper as the background; nav links on the accent-900 bar
  use accent-300, not accent.

## Assets
- **Fonts**: Barlow + Barlow Condensed via the Google Fonts import in `styles.css`.
- **Icons**: Lucide (https://lucide.dev) at stroke-width 1.5. The prototype uses text glyphs
  (≡ ‹ › ＋ ☆ ★ ✕) as placeholders — replace with Lucide icons (`menu`, `chevron-left`,
  `chevron-right`, `plus`, `star`, `x`).
- **Book covers**: none supplied. Every cover is a `.blueprint.duotone` placeholder on
  `--color-surface`; real covers must be wrapped in `.duotone` so they take the steel wash,
  square and hairline-framed.
- **Channel logos**: not supplied; the colored swatch stands in. If logos are added, keep them
  monochrome or duotoned.

## Files
- `Book Price Portal.dc.html` — the full prototype (all six screens, desktop + mobile). Markup first,
  then the `class Component` block holding mock data (`BOOKS`, `SHOPS`, `SHOP_META`, `SHOP_TINT`,
  `FACETS`) and all behaviour in `renderVals()`.
- `support.js` — prototype runtime only; do not port.
- `_ds/industry-9b214514-7f4f-46f2-be71-47cae6566b57/styles.css` — the design system's token +
  component stylesheet (source of truth for all values above).
- `_ds/industry-9b214514-7f4f-46f2-be71-47cae6566b57/readme.md` — the Industry design-system guide
  (direction, do/don't, component classes).
- `_ds/industry-9b214514-7f4f-46f2-be71-47cae6566b57/_ds_bundle.js` — design-system bundle loaded by
  the prototype.

To view the prototype: serve the folder (`npx serve .`) and open `Book Price Portal.dc.html` — the
relative `_ds/` and `support.js` paths must stay intact.
